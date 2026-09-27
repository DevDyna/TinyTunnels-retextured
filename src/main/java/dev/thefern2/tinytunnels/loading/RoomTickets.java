package dev.thefern2.tinytunnels.loading;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps a room's chunk loaded exactly while its machine's chunk is ticking ("follow the host").
 *
 * <p>This follows ticking, not block entity load/unload: a room ticket keeps nearby chunks in memory
 * without ticking them, so a machine standing near another room would otherwise never unload and
 * would hold its own room forever. Loaded machines register here; every second (and right after
 * any machine loads, unloads or is bound) the held tickets are reconciled against which machines
 * are in ticking chunks.
 *
 * <p>Tickets are owned by the room's UUID. On world load every saved ticket is dropped, so a crash
 * can never leave a room loaded forever.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class RoomTickets {
    public static final TicketController CONTROLLER = new TicketController(TinyTunnels.id("rooms"), (level, helper) -> {
        List<UUID> owners = List.copyOf(helper.getEntityTickets().keySet());
        owners.forEach(helper::removeAllTickets);
    });

    /**
     * While a player is inside a room, keeps that room's machine chunk loaded too (and, for nested
     * rooms, every machine up the chain), so tunnels keep working while you watch from inside.
     */
    public static final TicketController OCCUPANCY = new TicketController(TinyTunnels.id("occupancy"), (level, helper) -> {
        List<UUID> owners = List.copyOf(helper.getEntityTickets().keySet());
        owners.forEach(helper::removeAllTickets);
    });

    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int MAX_NESTING = 8;

    /** Room id -> the machine position whose chunk is held because someone is inside that room (or a room nested in it). */
    private static final Map<UUID, GlobalPos> OCCUPIED = new HashMap<>();

    private static final Set<MachineHost> MACHINES = new HashSet<>();
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static boolean dirty;

    @SubscribeEvent
    static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
        event.register(OCCUPANCY);
    }

    /** A machine block entity was loaded on the server. */
    public static void track(MachineHost machine) {
        MACHINES.add(machine);
        dirty = true;
    }

    /** A machine block entity was unloaded or removed. */
    public static void untrack(MachineHost machine) {
        MACHINES.remove(machine);
        dirty = true;
    }

    /** Something changed (e.g. a machine got bound to a room); reconcile on the next tick. */
    public static void requestUpdate() {
        dirty = true;
    }

    /** Machines currently loaded on the server. */
    public static Set<MachineHost> machines() {
        return Collections.unmodifiableSet(MACHINES);
    }

    public static Set<UUID> active() {
        return Collections.unmodifiableSet(ACTIVE);
    }

    public static Map<UUID, GlobalPos> occupied() {
        return Collections.unmodifiableMap(OCCUPIED);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!dirty && server.getTickCount() % CHECK_INTERVAL_TICKS != 0) return;
        dirty = false;
        reconcile(server);
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        dirty = true;
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        dirty = true;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        dirty = true;
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        MACHINES.clear();
        ACTIVE.clear();
        OCCUPIED.clear();
        dirty = false;
    }

    private static void reconcile(MinecraftServer server) {
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        if (rooms == null) return;
        RoomData data = RoomData.get(server);
        reconcileOccupancy(server, rooms, data);

        Set<UUID> wanted = new HashSet<>();
        for (MachineHost machine : MACHINES) {
            UUID roomId = machine.getRoomId();
            if (roomId != null && !machine.isRemoved() && machine.getLevel() instanceof ServerLevel level
                    && level.shouldTickBlocksAt(machine.getBlockPos())) {
                wanted.add(roomId);
            }
        }

        for (UUID roomId : List.copyOf(ACTIVE)) {
            if (!wanted.contains(roomId)) {
                ACTIVE.remove(roomId);
                data.room(roomId).ifPresent(room -> force(rooms, room, false));
            }
        }
        for (UUID roomId : wanted) {
            if (!ACTIVE.contains(roomId)) {
                data.room(roomId).ifPresent(room -> {
                    ACTIVE.add(roomId);
                    force(rooms, room, true);
                });
            }
        }
    }

    /** Holds the machine chunk of every room a player is in, walking up through nested rooms. */
    private static void reconcileOccupancy(MinecraftServer server, ServerLevel rooms, RoomData data) {
        Map<UUID, GlobalPos> wanted = new HashMap<>();
        for (ServerPlayer player : rooms.players()) {
            if (player.isSpectator()) continue;
            Room room = data.byChunk(player.chunkPosition()).orElse(null);
            for (int depth = 0; room != null && depth < MAX_NESTING; depth++) {
                GlobalPos host = room.host().orElse(null);
                if (host == null || wanted.containsKey(room.id())) break;
                wanted.put(room.id(), host);
                room = host.dimension() == rooms.dimension()
                        ? data.byChunk(new ChunkPos(host.pos().getX() >> 4, host.pos().getZ() >> 4)).orElse(null)
                        : null;
            }
        }

        for (Map.Entry<UUID, GlobalPos> held : List.copyOf(OCCUPIED.entrySet())) {
            if (!held.getValue().equals(wanted.get(held.getKey()))) {
                OCCUPIED.remove(held.getKey());
                forceHost(server, held.getKey(), held.getValue(), false);
            }
        }
        wanted.forEach((roomId, host) -> {
            if (!OCCUPIED.containsKey(roomId)) {
                OCCUPIED.put(roomId, host);
                forceHost(server, roomId, host, true);
            }
        });
    }

    private static void forceHost(MinecraftServer server, UUID roomId, GlobalPos host, boolean add) {
        ServerLevel level = server.getLevel(host.dimension());
        if (level == null) return;
        boolean changed = OCCUPANCY.forceChunk(level, roomId, host.pos().getX() >> 4, host.pos().getZ() >> 4, add, true);
        // TODO(debug): remove once occupancy loading is verified in-game.
        TinyTunnels.LOGGER.info("[TT-DEBUG] occupancy {} room={} host={} in {} forceChunk returned {}",
                add ? "ADD" : "REMOVE", roomId.toString().substring(0, 8), host.pos().toShortString(), host.dimension().identifier(), changed);
    }

    private static void force(ServerLevel rooms, Room room, boolean add) {
        ChunkPos chunk = room.geometry().chunk();
        int chunkX = chunk.getMinBlockX() >> 4;
        int chunkZ = chunk.getMinBlockZ() >> 4;
        boolean changed = CONTROLLER.forceChunk(rooms, room.id(), chunkX, chunkZ, add, true);
        // TODO(debug): remove once follow-the-host loading is verified in-game.
        TinyTunnels.LOGGER.info("[TT-DEBUG] ticket {} room={} chunk={},{} forceChunk returned {}",
                add ? "ADD" : "REMOVE", room.id().toString().substring(0, 8), chunkX, chunkZ, changed);
    }

    private RoomTickets() {}
}
