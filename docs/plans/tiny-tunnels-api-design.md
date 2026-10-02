# Tiny Tunnels API Design

Phase A1 of `tiny-tunnels-api-and-addons.md`: the public API on paper, checked against the three tunnel kinds before any code moves.

**Status (2026-10-01):** design agreed: all six decisions confirmed (see "Decisions"). **The `api` package is written** (`core/src/main/java/dev/thefern2/tinytunnels/api/`), interfaces, records and events with Javadoc; core doesn't implement it yet (A2). The sketches below match the code; the code and its Javadoc are the reference. "Settled while writing the code" lists what A1 added or changed.

## How the three kinds use a machine face

The API has to cover three quite different kinds, so here's how each one works today:

| | Transfer (items, fluids, energy) | Redstone | Kinetic (Create addon, with the port block) |
|---|---|---|---|
| **Wall block** | `TunnelWallBlock` + block entity (buffer). States `FACE`, `INWARD`, `MODE`. | `RedstoneTunnelWallBlock` + block entity (load hook). States `FACE`, `INWARD`, `MODE`, `POWERED`. | Addon wall + Create block entity. States `FACE`, `INWARD`, `MODE`. |
| **Per-tunnel data** | `TunnelMode` (pass-through, buffered in, buffered out) | `RedstoneMode` + last `power` | IN/OUT |
| **Machine face** | The **machine block** answers item, fluid and energy capability lookups on the face (`TunnelCapabilities.fromMachine`). | The **machine block** emits and reads the signal (`getSignal`, `isSignalSource`, `neighborChanged`, scheduled tick). | **Nothing on the machine.** The addon's port block sits against the face. |
| **Client needs** | Which faces have a transfer tunnel, so pipes see an empty handler on exactly those faces (the Pipez flange fix). | Whether the machine is a signal source at all (`isSignalSource(state)` gets only the block state). | Nothing. |
| **Removal** | Hands back buffered items; fluid needs a second click to discard. | Stops emitting on that face. | Nothing; the ends see the tunnel gone. |
| **Looks** | Blue letter; buffered marks on the wall. | Red letter, lit while powered. | Brass letter. |

So the API isn't one "machine face hook":
- **Capabilities** stay NeoForge capabilities. Any mod can register its own on core's machine blocks and wall blocks, and the API tells it what's on the other side.
- **Redstone** is core-internal on the machine block. Addons don't need it.
- **Kinetic** needs nothing from the machine except finding the tunnel on a face.

## API surface

### Tunnel kinds

A NeoForge custom registry, `tinytunnels:tunnel_kind`. Addons register with a `DeferredRegister` on it. Core's own kinds are `tinytunnels:transfer` and `tinytunnels:redstone`; the Create addon's is `tinytunnels_create:kinetic`.

```java
public interface TunnelKind<D> {
    /** Per-tunnel data, saved in the room. */
    Codec<D> dataCodec();
    D defaultData();

    /** The kind's wall block, and the state to build for a tunnel (placement, shell repair, face moves). */
    Block wallBlock();
    BlockState wallState(Direction face, Direction inward, D data);

    /** At most this many tunnels of this kind per room (6 = one per face, no extra limit). */
    default int maxPerRoom() { return 6; }

    /** A reason this tunnel can't go here, or null. Checked after core's own checks; nothing is used up on a refusal. */
    default @Nullable Component refusePlacement(PlacementContext<D> context) { return null; }

    /** The item a removed tunnel gives back (not in creative). */
    ItemStack item(D data);

    /** Asked before a wrench removal. Null to go ahead; otherwise shown, and the removal waits for a second click. */
    default @Nullable Component confirmRemoval(TunnelContext<D> tunnel, Player player) { return null; }

    /** Called just before the wall turns back into a room wall: hand back contents, clear state. */
    default void onRemoving(TunnelContext<D> tunnel, @Nullable Player player) {}

    /** Empty-hand click on the wall (IN/OUT, mode cycle). Return PASS to do nothing. */
    default InteractionResult useWithoutItem(TunnelContext<D> tunnel, Player player) { return InteractionResult.PASS; }

    /** The overlay on the machine face, and the name and one-line status for Jade and the room command. */
    FaceLook faceLook(D data);
    Component displayName();
    default @Nullable Component describe(D data) { return null; }
}

/** Textures for the machine-face overlay: one per face, keyed by the face letter. */
public record FaceLook(ResourceLocation texturePrefix) {}   // "<ns>:block/machine_port_<kind>" + "_<face>"
```

- **Wrench moving a tunnel:** core does it for every kind. It picks the next free face, rebuilds the wall with `wallState(newFace, …)` (same block, so the block entity stays), updates the room and fires `TunnelEvent.Moved`.
- **Buckets, buffered modes and similar:** the wall block belongs to the kind, so kind-specific clicks stay on the kind's own block. `useWithoutItem` exists so core's wrench and click routing stays in one place.

### Reading rooms and machines

```java
public interface TunnelService {
    static TunnelService get() { ... }            // core's implementation, found with ServiceLoader

    @Nullable ServerLevel roomLevel(MinecraftServer server);
    Optional<MachineView> machineAt(Level level, BlockPos pos);
    Optional<RoomView> room(MinecraftServer server, UUID id);
    Optional<RoomView> roomAt(ServerLevel rooms, BlockPos pos);   // the room whose space contains pos
    /** Core's machine blocks (every size), for registering capabilities on them. Kinds' wall blocks aren't in it. */
    List<Block> machineBlocks();
}

public interface MachineView {
    BlockPos pos();
    Level level();
    @Nullable UUID roomId();
    /** The room, only while this machine is its current host (not a stale or duplicated machine). */
    Optional<RoomView> hostedRoom();
}

public interface RoomView {
    UUID id();
    int size();
    RoomShape shape();                              // inwardNormal, wallCenter, isShell, isInterior, contains
    Optional<GlobalPos> host();
    Map<Direction, Tunnel<?>> tunnels();            // by machine face; registered kinds only
    boolean isFaceUsed(Direction face);             // also counts tunnels of unknown kinds
    Optional<Tunnel<?>> tunnelAt(BlockPos wall);
    <D> Optional<Tunnel<D>> tunnel(Direction face, TunnelKind<D> kind);
}

public record Tunnel<D>(TunnelKind<D> kind, Direction face, BlockPos wall, Direction inward, D data) {
    public <E> Optional<Tunnel<E>> as(TunnelKind<E> kind) { ... }   // typed, or empty for another kind
}
```

- `MachineHost` stays internal; `MachineView` is the public face of it.
- `RoomShape` is today's `RoomGeometry`, trimmed to what addons need.

### Changing tunnels

```java
public interface TunnelService {
    /** Places a tunnel at a wall block, on the first free face. Does every check, the wall, room data and events. */
    <D> PlaceResult place(ServerLevel rooms, BlockPos wall, TunnelKind<D> kind, D data, @Nullable Player player);
    /** Each returns false, changing nothing, when there's no such tunnel (or newFace is used). */
    boolean move(RoomView room, Direction face, Direction newFace);
    boolean remove(RoomView room, Direction face, @Nullable Player player);
    <D> boolean setData(RoomView room, Direction face, TunnelKind<D> kind, D data);
}

public sealed interface PlaceResult {
    record Placed(Direction face) implements PlaceResult {}
    record Refused(Component reason) implements PlaceResult {}
}
```

- **Core's checks, in order:** the room dimension; a room wall block (not a tunnel); not an edge or corner; a free face; the kind's `maxPerRoom`; then the kind's `refusePlacement`.
- **One service for every change** means shell protection, the face letters and the capability refresh happen once, in core, whichever mod made the change.

### The other side

For capability addons, such as Mekanism chemicals:

```java
public interface TunnelService {
    /**
     * For a machine face: the block inside the room next to that face's tunnel wall. For a tunnel wall: the block
     * outside next to its machine face. Only loaded positions; never loads chunks.
     */
    Optional<Endpoint> otherSide(Level level, BlockPos pos, Direction side);

    /** Runs a lookup that may pass through more tunnels (nested machines) with core's loop and depth guard. */
    <T> @Nullable T guarded(Supplier<@Nullable T> lookup);
}

public record Endpoint(ServerLevel level, BlockPos pos, Direction side) {}
```

A chemicals addon would:
1. register its capability on `machineBlocks()` and on its own wall block
2. answer with `otherSide(...)` plus a `guarded(...)` lookup of the capability there

That's what `TunnelCapabilities` does for items, fluids and energy today. Caching (`EndpointCaches`) stays internal for now. It can be exposed later if an addon needs it for speed.

### Events

Posted on `NeoForge.EVENT_BUS`, server side only.

| Event | When |
|---|---|
| `TunnelEvent.Added`, `.Moved`, `.Removed`, `.DataChanged` | After the room data changed. Carries the room dimension, the room, the face (and the old face for `Moved`, the old data for `DataChanged`) and the `Tunnel`. |
| `MachineEvent.Placed`, `.Removed` | After a machine is bound to its room, and before a machine goes (the room still has its host). Only for the room's current host; not on chunk unload. |
| `RoomEvent.StartedTicking`, `.StoppedTicking` | When core adds or drops the room's chunk ticket. Carries the room dimension and the room. Fired by `RoomTickets`' reconcile, so on the tick after a machine is placed or picked up, not in the same call. |

`DataChanged` fires often for redstone (every power change), so listeners must be cheap. Core itself only redraws a face when the `FaceLook` changes.

### Guarantees

- A room ticks exactly while its machine's chunk ticks.
- Tunnel walls can't be broken, and the shell repair rebuilds every tunnel from `wallState`. A wall already in the right state is left alone, so its block entity survives.
- A kind's data survives its mod being removed (see "Unknown kinds").

## Machine face looks (A3)

**Problem:** today the face letters come from `PortKind`, an enum in the machine's block states. An addon can't add values, and with 5 values on 6 faces there are already 15,625 states per machine block.

**Proposal: looks from the block entity, not the block state.**
- The machine block entity sends each face's `FaceLook` (a texture prefix) to the client in its update tag.
- A baked model overlay reads it through NeoForge `ModelData` and draws the face letter from the kind's textures.
- There's no limit on kinds, and a mod can add a look just by shipping textures.
- The machine block keeps **one** block state property, `signal` (true while any face has a redstone tunnel), because `isSignalSource(BlockState)` gets only the state.
- The machine goes from thousands of states per size to 2.

The client also reads the same synced data for the "empty handler on tunnel faces" check (the Pipez fix). It needs "which faces have a transfer tunnel", which the synced per-face kind id gives.

**Alternative:** a fixed set of generic looks in the block state, with kinds assigned to slots. It's simpler to render, but it has a hard limit, the state count stays high, and two kinds sharing a slot would confuse the client capability check. Kept only as a fallback.

### A3 build approach (client engineer, agreed 2026-10-01)

**Today:** `MachineBlock.PORTS` has one `EnumProperty<PortKind>` per face, set by `withPorts`/`applyPorts`/`withRedstoneLit`, drawn by a datagen multipart with 24 overlay models. Read by `TunnelCapabilities.fromMachine` (the Pipez client check), `MachineBlock`'s redstone methods, Jade, `KineticMachineBlock` and the GameTests.

1. **Synced data, `machine/MachineFaces`:** an immutable record, per face a kind id and a `FaceLook` (or null). NBT, a `ModelProperty`, `kindAt`/`has(face, kindId)`, `fromRoom(Room)`. Update tag: `"faces": {"north": {"kind": "tinytunnels:transfer", "look": "tinytunnels:block/machine_port_transfer"}}`. Not saved to disk (derived from the room). A machine that isn't the host gets none.
2. **Server (`MachineCore`):** `getUpdateTag` computes the faces from `hostedRoom()`. `syncFaces()` recomputes, and on a change stores them, sends a block entity update (`UPDATE_CLIENTS`) and sets `signal`. `syncMachineFaces` and `updateMachineLook` call it. A redstone power change resends only when the look crosses lit/unlit.
3. **Client receive:** on a change, `requestModelDataUpdate()`, dirty the section, `invalidateCapabilities(pos)`.
4. **Overlay model (`client/`):** `ModelEvent.ModifyBakingResult` wraps every machine state's model in a `BakedModelWrapper` that adds one quad per face with a look (`FaceBakery`, full face 0.01 outside, cullface that face), sprite `look.texture(face)` from the block atlas, cached per (texture, face).
5. **`signal` state:** `PORTS` goes; `SIGNAL` is true while any face has a redstone tunnel. Per-face redstone reads come from the block entity's `MachineFaces`. `syncFaces` does the neighbour and shape updates when the set of redstone faces changes, since the state no longer does. `onRemove` reads the block entity before `super`.
6. **Client capability check:** `fromMachine`'s client branch asks `faces().has(side, transfer id)`. An unknown kind is never transfer.
7. **Textures:** renamed, no pixel changes, face last: `machine_port_transfer_<f>`, `machine_port_redstone_<f>`, `machine_port_redstone_on_<f>` (lit), `machine_port_kinetic_<f>` (moves to `tinytunnels_create` in A6). The 24 overlay JSONs go; the machine blockstate is a base-only multipart. An unknown kind syncs its id with no look.
8. **Before A2 lands,** `MachineFaces.fromRoom` maps today's three room maps to fixed ids and looks; after A2.2/A2.3 it uses `kind.faceLook(data)`. That's the only place that changes.

**Lead decisions (2026-10-01):**
- **Kinetic shaft axis until A6:** `KineticMachineBlock` gets a Create-only 7-value `kinetic_face` property (`IRotate.getRotationAxis` only gets the state). 14 states per size with Create, 2 without; dropped in A6.
- **Texture folder rule:** look textures must be under `<ns>:textures/block/`, which the block atlas stitches. In the `FaceLook` Javadoc and the A8 guide.
- **Parked for A8:** a public client-side read of a machine's face kinds, so a capability addon can do its own Pipez-style client check.


## Save format

A room keeps one map, **face → tunnel**:

```
"tunnels": {
  "north": { "kind": "tinytunnels:transfer", "wall": [x, y, z], "data": "buffered_in" },
  "up":    { "kind": "tinytunnels:redstone", "wall": [x, y, z], "data": { "mode": "out", "power": 15 } },
  "east":  { "kind": "tinytunnels_create:kinetic", "wall": [x, y, z], "data": "out" }
}
```

- `data` is decoded with the kind's `dataCodec`.
- **Unknown kinds:** if the kind isn't registered, core keeps the entry as it was (`kind`, `wall`, raw `data` tag) and saves it back unchanged. It places a generic inert tunnel wall (`tinytunnels:unknown_tunnel_wall`) so the shell has no hole. The face counts as used. When the kind is back, the entry decodes and the shell repair builds the real wall. Also kept raw: an entry of a registered kind whose `data` fails to decode (logged as a warning); its wall is left alone if it's already that kind's block, so a block entity such as a buffer survives. The wrench can't move or remove a raw entry; it says the kind's mod isn't loaded (A5, 2026-10-01).
- **Dev worlds:** `Room.CODEC` read the old `tunnels` / `redstone` / `kinetic` fields once, until those readers were removed on 2026-10-02 (start new worlds). No player world has them.

## Checking the design against each kind

**Transfer (core)**
- **Data:** `TunnelMode`. `wallState` sets `FACE`, `INWARD`, `MODE`.
- **Capabilities:** core registers them on the machine blocks and on the transfer wall, as now. `fromMachine` becomes `tunnel(face, TRANSFER)` plus the existing proxy.
- **Removal:** `confirmRemoval` returns the "holds X mB, click again" message when the buffer has fluid. `onRemoving` hands back items and discards fluid. That moves the two-click logic out of `TunnelWrenching`, into the kind.
- **Mode cycle:** `useWithoutItem`. Buckets stay on the wall block.
- **Refresh:** core refreshes neighbours' capabilities directly in its change path (`TunnelChanges`), before posting the `TunnelEvent`, so the order is fixed. It doesn't listen to its own events (settled in A2.5).
- **Gaps:** none.

**Redstone (core)**
- **Data:** a record `(RedstoneMode mode, int power)`. `wallState` also sets `POWERED`.
- **Machine side:** stays internal. The machine's redstone methods ask for `tunnel(face, REDSTONE)`; `isSignalSource` uses the `signal` state property.
- **Power:** a change is `setData`, which fires `DataChanged`. `faceLook` returns the lit or unlit texture, so the face redraws only when it crosses 0.
- **Direction flip:** `useWithoutItem`.
- **Gap:** power changes go through the public service, which is more machinery than today's direct `RoomData` call. Keep an internal fast path for power if profiling shows it.

**Kinetic (Create addon)**
- **Data:** IN/OUT. `wallState` sets `FACE`, `INWARD`, `MODE`. The wall block and block entity belong to the addon.
- **Wall end:** `roomAt(rooms, pos).tunnelAt(pos)`.
- **Port end:** `machineAt(level, pos.relative(facing.getOpposite()))` → `hostedRoom()` → `tunnel(facing, KINETIC)`. The port's Jade line says "not linked" when there's none.
- **Limit:** `maxPerRoom` is 6 once A7 has checked that networks can't loop; 1 until then.
- **Direction flip:** `useWithoutItem`. The removal hooks aren't needed, because the ends see the tunnel gone.
- **Loading:** relies on the ticking guarantee. The `RoomEvent`s aren't needed; the stale-link rule already covers load timing.
- **Gaps:** none. It needs fewer API parts than the core kinds, which is the point of the port block.

**A capability addon (Mekanism chemicals, for comparison)**
- Registers its capability on `machineBlocks()` and its wall, and answers with `otherSide` plus `guarded`.
- Needs a machine-face capability registration on **core's** blocks: NeoForge allows it.
- **Open:** Mekanism chemicals may need a buffered mode like the transfer tunnel; that's the addon's own block entity.

## Decisions (confirmed 2026-10-01)

1. **Registry and service style:** a NeoForge custom registry for kinds, plus a static `TunnelService.get()` that finds core's implementation (see below). The addon declares `ordering="AFTER"` on `tinytunnels`.
2. **Face looks from the block entity through `ModelData`**, with one `signal` block state property (see "Machine face looks"). Accepted: A3 takes longer (a custom baked model), in exchange for no limit on kinds.
3. **The generic inert wall** for unknown kinds, and keeping raw entries.
4. **Wrench cycle and removal done by core** for every kind, with the kind hooks above.
5. **Capability plumbing stays NeoForge-native** (`otherSide` plus `guarded`), with no Tiny Tunnels transfer abstraction in the API. The API stays minimal for now; an easier "transfer kind" path can be added later if modders ask for it.
6. **`MachineHost` stays internal**; `MachineView` is public.

## Settled while writing the code (A1, 2026-10-01)

- **`TunnelService.get()`** uses `ServiceLoader.load(TunnelService.class, TunnelService.class.getClassLoader())`; core ships `META-INF/services/dev.thefern2.tinytunnels.api.TunnelService` (A2). There's no public setter. Until core provides it, `get()` throws `IllegalStateException("Tiny Tunnels isn't loaded")`. Confirmed in A2.5 (GameTest `api_service_found`, 2026-10-01): FML's module layer finds it, so no fallback is needed.
- **The registry object is in the API:** `TunnelKinds.REGISTRY_KEY` (`tinytunnels:tunnel_kind`) and `TunnelKinds.REGISTRY`, built with `new RegistryBuilder<>(key).sync(true).create()`. Core registers it in `NewRegistryEvent` (A2). Synced: a client without an addon can't join a server that has it, which is right, since the addon has blocks.
- **`PlacementContext<D>`** (record): the room dimension, the room, the wall, the face it would get, the inward direction, the data, and the player or null.
- **`TunnelContext<D>`** (record): the room dimension, the room, the `Tunnel`.
- **`FaceLook`** is one texture prefix; `texture(face)` gives `prefix + "_" + face`. Redstone's lit look is a second `FaceLook` with its own prefix.
- **`move`, `remove`, `setData` return `boolean`** instead of `void`, so a call on a missing tunnel isn't silent. `remove` doesn't give the item back or ask `confirmRemoval`; the wrench does both.
- **`machineBlocks()`** is core's machine blocks only. A capability addon registers on its own wall block itself.
- **`RoomView.isFaceUsed(face)`** counts tunnels of unknown kinds; `tunnels()` returns only registered kinds.
- **Events** extend `net.neoforged.bus.api.Event`, and none can be cancelled. `TunnelEvent` and `RoomEvent` also carry the room dimension (`level()`), so listeners don't have to look it up. `DataChanged` carries the old data. `Moved` keeps the wall; the data stays too, except what a kind resets on a move (redstone's power goes to 0, as before A2).
- **Nullability:** `org.jspecify.annotations.Nullable`, as in the rest of the code.
- **`RoomShape`** has `contains`, `isInterior`, `isShell`, `wallCenter`, `inwardNormal`. Core's `RoomGeometry` can implement it.

## Added for the Create addon (A6, 2026-10-01)

Requested by the Create addon engineer while moving the kinetic tunnel onto the API:
- **`TunnelService.wallRemoved(level, pos, oldState)`:** an addon's wall block calls it from `onRemove`, so core repairs a tunnel wall removed by a command or another mod, as it does for its own walls. Core implements it with its shell repair.
- **`TunnelKind.placedMessage(face, data)`:** the action-bar message after placing or a wrench move ("Rotation out to the machine's east side"); null keeps core's generic one.
- **`CoreIds`:** `ROOM_WALL` (the block a tunnel item is used on, readable on the client) and `CREATIVE_TAB` (core's tab, for addons' items).

## Out of scope for A1

- Client API: Jade providers belong to each kind's mod. Core's machine tooltip lists tunnels with `displayName` and `describe`.
- Contraptions moving machines or ports.
- Porting to `main`: the API is Minecraft-version specific (`ResourceLocation`, capability types). Each branch gets its own copy (A9).
