# Compact Machines Tunnel Issues: Lessons for Tiny Tunnels

Research date: 2026-09-26. Source: the CompactMods/CompactMachines GitHub issues and PRs, plus the tunnel source on the `1.12.x`, `1.16.x`, `1.18.x` and `1.19.x` branches.

`#N` = `https://github.com/CompactMods/CompactMachines/issues/N`.

## Scope

- **"tunnel" search:** 93 results, which is 82 issues (80 closed, 2 open: #609, #656) and 11 PRs.
- **Open storage-based replacement work:** #658, #659, #660.
- **Broader search** (20 terms: pipe, capability, chunk, AE2, Mekanism, crash, …): 405 unique issues. Most non-tunnel ones are about crashes, previews and teleporting.

**How the maintainer explains it (#609):** "several attempts at building tunnels against Forge capabilities from 1.16 through 1.19, and every single attempt failed… Pipes would not connect, people got confused by the buffer system (the only way to FIX said pipe issue), things got lost… capability APIs changed constantly."

- Tunnels were cut in CM7 (1.21).
- The planned replacement (#656, "named storage") is storage-based, not passthrough.

## What each CM generation did, and why it broke

| Version | Design | Why it failed |
|---|---|---|
| CM1 (1.7.10) | Interface blocks with per-side buffers, I/O modes, hand-written AE2 and gas compat | Buffers voided or clogged items (#10, #25); custom compat burned out the author |
| **CM3 (1.10–1.12)** | **Pure passthrough.** `TileEntityTunnel.getCapability` → saved data → `realWorld.getTileEntity(machinePos.offset(side)).getCapability(...)`, mirrored inward. [source](https://github.com/CompactMods/CompactMachines/blob/1.12.x/src/main/java/org/dave/compactmachines3/tile/TileEntityTunnel.java) | Synchronous loads of unloaded chunks (#419); a static saved-data singleton that was null during world load (#314); no recursion guard (#382); client-side stubs (#288). **The design players remember fondly** (#609, #568). |
| CM4 (1.16) | `TunnelDefinition` registry forwarding the far side's `LazyOptional` | Forge had dropped `hasCapability`, so pipes wouldn't connect until an endpoint existed; forwarded `LazyOptional`s were never invalidated; **wrong side argument** (passed the wall side, not the connected face) ([source](https://github.com/CompactMods/CompactMachines/blob/1.16.x/src/main/java/com/robotgryphon/compactmachines/tunnels/definitions/ItemTunnelDefinition.java)) |
| CM4/5 (1.18–1.19) | **Buffered** tunnels (10 item slots, 10k FE in the tunnel block entity), `TunnelConnectionGraph` keyed by absolute machine position | Push-only mods never drained the buffer; items sat there or were voided; the graph went stale when the machine moved; no invalidation or neighbour updates; chunk loading was upgrade-only, then permanently forced (PR #580) |
| 1.20 | `tunnels-v2` graph rewrite | Abandoned. Tunnels were removed in CM7. |

**Pattern:** the passthrough approach (CM3) failed on loading and safety, and the buffer approach (CM4/5) failed on semantics. **No generation ever invalidated capability caches correctly.** That's the part NeoForge's `BlockCapabilityCache` and `invalidateCapabilities` now make possible.

## Failure categories and how our design covers them

| # | Category | Representative issues | Root cause | Our design |
|---|---|---|---|---|
| A | Pipes not connecting or not transferring (largest group, ~25) | #201, #276, #433, #512, #524, #545, #567, #568, #587 | Without `hasCapability`, a pipe only connects if a handler exists **now**; buffers were the workaround, and Mekanism, Powah, IE and XNet never pull from passive buffers (matrix on #568) | ✅ Passthrough restores push. ⚠️ Returning `null` while the far side is empty means pipes connect late. **Fix below: return an empty handler instead of null when a tunnel exists.** |
| B | Voided or lost items | #10, #512, #531, #532, #565, #622 | Buffers in the tunnel block entity that nothing drained | ✅ No buffers, ever |
| C | Stale mapping or caches | #78, #405, #524, #525, #532, #546, #571 | Absolute positions stored in the tunnel block entity; no invalidation; at restart the overworld loads before the room, so pipes cache "nothing" | 🟡 Invalidation covers most of it. ⚠️ Add: the tunnel block entity's `onLoad` invalidates the host side; resolve the host at query time; also send neighbour updates. |
| D | Chunk loading | #326, #419, #545, #556, #563, #568, #611, #615, PR #580 | Rooms never auto-loaded; later permanently forced plus `save(true)`; FTB Chunks blocked fake-player loaders (#563) | ✅ A ticket only while the host is loaded. ⚠️ Never touch block entities in unloaded chunks; check the FTB Chunks interaction. |
| E | Recursion and feedback loops | #285 (RF bouncing), #382 (StackOverflow, world crash-looped) | No guard | 🟡 The lookup guard helps. ⚠️ Loops at **transfer time** need a reentrancy-guarded delegate, which rules out handing back the raw handler in every case. |
| F | Crashes and bricked worlds | #223, #288, #303, #314, #390, #402, #438, #449 | Capabilities queried during world load before saved data or the dimension existed; server-only code running on the client; NPEs in redstone tunnels | ⚠️ Not covered explicitly. **Add a "capability path never throws" rule.** |
| G | Wrong face mapping | #368 (off-hand double-fire, PR #369), #549, 1.16 side bug | Wrong side argument; duplicate packets | ⚠️ Needs a 6×6 face test matrix |
| H | UX confusion | #201, #250, #283, #398, #556, #587 | Buffers; no direction indicator; no docs | 🟡 No buffers. ⚠️ Add face indicators and docs. |
| I | Specific mods | AE2 (#32, #58, #77, #264, #449, #461, #531), RS (#276, #370, #552), Mekanism (#36, #364, #512, #567, #568), Thermal, EnderIO, Powah, IE, XNet, Pipez, BuildCraft, GTCE | Push-only pipes; the AE2 grid isn't a capability; chemicals/heat/EU aren't FE, items or fluids | ✅ The push/pull group is fixed. ⚠️ AE2 **grid cables**, Mekanism chemicals and heat need separate tunnel types or are out of scope. |
| J | Performance | #285, #419, #580 | Bouncing, synchronous loads, `save(true)` | ✅ Nothing ticks |
| K | Multiplayer | #288, #390 | Client/server split | ⚠️ Server authority; the client returns null |
| L | Dupes | none from tunnels (CM's dupes came from teleporters: #33, #259, #260) | — | ✅ Low risk with transactions. Still test it. |

## What players asked for

- **High:**
  - Mekanism chemicals and gases (#36, #364, #568, #609). Nuclear waste is the showcase case.
  - AE2/RS cable passthrough (#264, #282, #373, #461, #552, #609).
  - "Just forward the capability", i.e. auto-push (#532, #567, #568).
- **Medium:**
  - Redstone in and out, including bundled (#152, #252, #283, #470).
  - Per-side I/O modes (#108, #118).
  - Several tunnel types on one face (#108, #568).
  - Energy limit config (#551).
  - Face indicator (#556).
  - Tunnels that survive moving the machine (#525, #546).
- **Low:**
  - Round-robin 1:n distribution (#244, declined by the dev).
  - Generic "any capability" forwarding: heat, air, EU, MJ, CC (#268, #312, #342, #348).
  - Filters: almost never requested.

## Changes this drives in our plan

### Design rules

1. **Never buffer.** The only tunnel state is its mapping.
2. **Empty handler, not null, when a tunnel exists.**
   - If a face has a tunnel but the far endpoint is unloaded, missing, or exposes nothing, return `EmptyResourceHandler` / `EmptyEnergyHandler`.
   - Pipes then connect immediately and move nothing until the far side appears, when invalidation swaps in the real handler.
   - Return `null` only when the face has no tunnel. This fixes category A without a buffer.
3. **Never load chunks from the capability path.** Check `isLoaded` first (#419).
4. **The capability path never throws.**
   - Return null or empty if the server, `RoomData`, the room level or the binding is missing, including during world load.
   - Catch, log once, and return empty (#303, #314, #438, #449).
5. **Server only.** The client returns null (#288).
6. **Resolve the host at query time** from `RoomData` (room → host). Tunnel block entities store only the room ID, face and inward direction, never an absolute host position (#546, #571).
7. **Invalidation points.** On each event, call `invalidateCapabilities` on both ends **and** `updateNeighborsAt` for pipes that only listen to block updates (#524):
   - tunnel placed, removed or cycled
   - host placed, broken or moved
   - host block entity `onLoad`
   - **tunnel block entity `onLoad`**, since the room loads after the host (#78, #532)
   - endpoint neighbour change (via the cache listener)
   - room ticket added or removed
8. **Two-layer recursion guard:**
   - at lookup (depth counter)
   - at transfer, where a thin guarded delegate re-enters the counter on `insert`/`extract` (#285, #382)
   - Wrap **always** (not only past depth 0). The delegate is a few lines, and the transfer-time loop can only be caught if we're in the call path.
9. **Face correctness.** Query the outside endpoint with `face.getOpposite()` and the inside with `inward.getOpposite()`, and cover all 36 combinations in tests (1.16 bug, #549).
10. **Tickets:** `TicketController` with a validation callback. Never `setChunkForced`, never force a save (#580).
11. **Face indicator:** the tunnel's texture or overlay shows its mapped face, and Jade shows it (#556).
12. **Out of scope for MVP, documented:**
    - AE2/RS **grid** cables (AE2 storage buses and import/export buses work, because they use capabilities)
    - Mekanism chemicals (optional tunnel type later)
    - heat, air, EU and MJ
    - redstone (Phase 7)

### GameTests and acceptance additions

- **Push-only:** a generator inside pushes out, and a consumer outside receives it with no pipe set to pull. A hopper pushing in lands items in the inner chest, or they're refused, never held (#565, #568, #622).
- **Face matrix:** all 6 wall × 6 face mappings route items, fluid and FE to the correct neighbour, including DOWN with hoppers (#368, #549).
- **Pipe before tunnel:** a pipe placed first connects once the tunnel is placed, without re-placing (#524).
- **Restart:** save and reload with pipes on both sides; they connect without manual action (#78, #532).
- **Move the host:** break and re-place elsewhere; the tunnels follow (#571 steps).
- **Unloaded ends:**
  - host chunk unloads → ticket released, capability empty, nothing lost; reload → it recovers
  - endpoint unloaded while a player is inside → empty, no synchronous load, no crash
- **Loops and nesting:**
  - two adjacent machines whose tunnels feed each other → no StackOverflow (#382)
  - a pipe network looping out and back in → bounded, no phantom draw (#285)
  - a nested machine feeding its parent's tunnel works, depth-guarded
- **Transactions:** an aborted transfer rolls back both sides; removing a tunnel mid-extract causes no dupe or void.
- **World load:** a capability query before `RoomData` exists → no NPE (#449).
- **Dedicated server:** a client standing next to a tunnel doesn't crash (#288, #390).
- **Mod smoke tests:** Pipez, Energized Power/Powah, Storage Drawers, RS3, AE2 storage/import/export bus, and Mekanism cables/transporters once released.
- **FTB Chunks** (if it's on 26.x): its `fake_players=check` doesn't block our tickets (#563). Our tickets use a UUID owner, not a fake player, so this should hold. Verify.
