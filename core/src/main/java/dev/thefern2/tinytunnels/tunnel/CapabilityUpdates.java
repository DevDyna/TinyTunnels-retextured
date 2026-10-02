package dev.thefern2.tinytunnels.tunnel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Tells pipes that what's behind a machine face or a tunnel changed. Deferred to the end of the
 * tick: invalidation listeners must not query capabilities, and neighbour updates from inside a
 * listener could. Each flush invalidates the position's capabilities and sends block updates, for
 * pipes that only re-check on neighbour changes.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class CapabilityUpdates {
    private record Target(ServerLevel level, BlockPos pos) {}

    private record LoadedChunk(ServerLevel level, ChunkPos pos) {}

    private static final Set<Target> PENDING = new LinkedHashSet<>();
    private static final Set<UUID> FACE_SYNC = new LinkedHashSet<>();
    // Chunk loads can arrive off the main tick; collected here and handled on the next tick.
    private static final Queue<LoadedChunk> LOADED_CHUNKS = new ConcurrentLinkedQueue<>();

    public static void schedule(ServerLevel level, BlockPos pos) {
        PENDING.add(new Target(level, pos.immutable()));
    }

    /**
     * Everything that exposes this room's tunnels changed: the machine, and every tunnel wall whose kind has
     * capabilities (not redstone or kinetic). Pass
     * the room as it was <em>before</em> a change when tunnels were removed, so their old positions
     * get notified too; the machine's face flags are synced from the latest data either way.
     */
    public static void roomChanged(MinecraftServer server, Room room) {
        FACE_SYNC.add(room.id());
        room.host().ifPresent(host -> {
            ServerLevel hostLevel = server.getLevel(host.dimension());
            if (hostLevel != null) schedule(hostLevel, host.pos());
        });
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        if (rooms == null) return;
        room.faces().values().forEach(tunnel -> {
            if (!(tunnel.kind() instanceof CoreKind<?> core) || core.hasCapabilities()) schedule(rooms, tunnel.wall());
        });
    }

    /**
     * A chunk next to a machine loaded: the machine's outside neighbour may have appeared, and a
     * cache we never filled can't tell us, so nudge that room's tunnels.
     */
    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) LOADED_CHUNKS.add(new LoadedChunk(level, event.getChunk().getPos()));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        handleLoadedChunks(event.getServer());
        syncMachineFaces(event.getServer());
        if (PENDING.isEmpty()) return;
        List<Target> targets = List.copyOf(PENDING);
        PENDING.clear();
        for (Target target : targets) {
            if (!target.level().isLoaded(target.pos())) continue;
            target.level().invalidateCapabilities(target.pos());
            target.level().updateNeighborsAt(target.pos(), target.level().getBlockState(target.pos()).getBlock());
        }
    }

    /** Only the machine's face flags need updating, e.g. after a redstone tunnel was added or moved. */
    public static void syncFaces(UUID roomId) {
        FACE_SYNC.add(roomId);
    }

    /** Sets each changed room's machine face flags (the letter overlays) to its current tunnels. */
    private static void syncMachineFaces(MinecraftServer server) {
        if (FACE_SYNC.isEmpty()) return;
        List<UUID> rooms = List.copyOf(FACE_SYNC);
        FACE_SYNC.clear();
        RoomData data = RoomData.get(server);
        for (UUID id : rooms) {
            Room room = data.room(id).orElse(null);
            GlobalPos host = room == null ? null : room.host().orElse(null);
            ServerLevel level = host == null ? null : server.getLevel(host.dimension());
            if (level == null || !level.isLoaded(host.pos())) continue;
            if (level.getBlockEntity(host.pos()) instanceof MachineHost machine) machine.core().syncFaces();
        }
    }

    private static void handleLoadedChunks(MinecraftServer server) {
        if (LOADED_CHUNKS.isEmpty()) return;
        List<LoadedChunk> chunks = new ArrayList<>();
        for (LoadedChunk chunk; (chunk = LOADED_CHUNKS.poll()) != null; ) chunks.add(chunk);
        for (MachineHost machine : RoomTickets.machines()) {
            if (!(machine.getLevel() instanceof ServerLevel level)) continue;
            int mx = machine.getBlockPos().getX() >> 4, mz = machine.getBlockPos().getZ() >> 4;
            for (LoadedChunk chunk : chunks) {
                if (chunk.level() == level && Math.abs((chunk.pos().getMinBlockX() >> 4) - mx) <= 1 && Math.abs((chunk.pos().getMinBlockZ() >> 4) - mz) <= 1) {
                    machine.getRoom().ifPresent(room -> roomChanged(server, room));
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        FACE_SYNC.clear();
        LOADED_CHUNKS.clear();
    }

    private CapabilityUpdates() {}
}
