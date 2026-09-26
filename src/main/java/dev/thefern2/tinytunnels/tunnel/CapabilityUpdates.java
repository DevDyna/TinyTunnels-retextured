package dev.thefern2.tinytunnels.tunnel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.machine.MachineBlockEntity;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.core.BlockPos;
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
    // Chunk loads can arrive off the main tick; collected here and handled on the next tick.
    private static final Queue<LoadedChunk> LOADED_CHUNKS = new ConcurrentLinkedQueue<>();

    public static void schedule(ServerLevel level, BlockPos pos) {
        PENDING.add(new Target(level, pos.immutable()));
    }

    /** Everything that exposes this room's tunnels changed: the machine, and every tunnel wall. */
    public static void roomChanged(MinecraftServer server, Room room) {
        room.host().ifPresent(host -> {
            ServerLevel hostLevel = server.getLevel(host.dimension());
            if (hostLevel != null) schedule(hostLevel, host.pos());
        });
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        if (rooms != null) room.tunnels().values().forEach(pos -> schedule(rooms, pos));
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
        if (PENDING.isEmpty()) return;
        List<Target> targets = List.copyOf(PENDING);
        PENDING.clear();
        for (Target target : targets) {
            if (!target.level().isLoaded(target.pos())) continue;
            target.level().invalidateCapabilities(target.pos());
            target.level().updateNeighborsAt(target.pos(), target.level().getBlockState(target.pos()).getBlock());
        }
    }

    private static void handleLoadedChunks(MinecraftServer server) {
        if (LOADED_CHUNKS.isEmpty()) return;
        List<LoadedChunk> chunks = new ArrayList<>();
        for (LoadedChunk chunk; (chunk = LOADED_CHUNKS.poll()) != null; ) chunks.add(chunk);
        for (MachineBlockEntity machine : RoomTickets.machines()) {
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
        LOADED_CHUNKS.clear();
    }

    private CapabilityUpdates() {}
}
