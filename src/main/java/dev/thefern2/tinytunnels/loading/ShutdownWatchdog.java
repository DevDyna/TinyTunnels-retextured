package dev.thefern2.tinytunnels.loading;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.room.RoomData;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * TODO(debug): remove once the 1.21.1 GameTest shutdown hang is understood.
 *
 * <p>The hang is vanilla's stop loop re-queuing the unload of a chunk that isn't ready for saving. The stuck
 * chunk never fires {@code ChunkEvent.Unload}, so this watches from outside instead: if the server is still
 * stopping after {@link #DELAY_MS}, it logs every chunk still waiting to unload, and whether it's a room chunk.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class ShutdownWatchdog {
    private static final long DELAY_MS = 10_000;
    private static final int MAX_LOGGED = 40;

    private static volatile boolean started;

    @SubscribeEvent
    static void onServerStarting(ServerStartingEvent event) {
        started = false;
    }

    /**
     * Starts watching on the tick {@code halt()} was called (the GameTest server halts from its own tick).
     * {@code ServerStoppingEvent} isn't used: on 1.21.1 it doesn't fire before the stop loop that hangs.
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.isRunning() || started) return;
        started = true;
        Set<Long> roomChunks = RoomData.get(server).rooms().stream()
                .map(room -> room.geometry().chunk().toLong())
                .collect(Collectors.toSet());
        Thread watchdog = new Thread(() -> watch(server, roomChunks), "TinyTunnels shutdown watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static void watch(MinecraftServer server, Set<Long> roomChunks) {
        try {
            Thread.sleep(DELAY_MS);
        } catch (InterruptedException e) {
            return;
        }
        if (server.isStopped()) return;
        TinyTunnels.LOGGER.warn("[TT-DEBUG] shutdown still running after {} ms; chunks waiting to unload:", DELAY_MS);
        for (ServerLevel level : server.getAllLevels()) {
            List<ChunkHolder> pending = pendingUnloads(level.getChunkSource().chunkMap);
            if (pending.isEmpty()) continue;
            TinyTunnels.LOGGER.warn("[TT-DEBUG]   {}: {} pending", level.dimension().location(), pending.size());
            pending.stream().limit(MAX_LOGGED).forEach(holder -> TinyTunnels.LOGGER.warn(
                    "[TT-DEBUG]     chunk {} room={} latest={} persisted={} ticketLevel={} generationRefs={} saveSyncDone={} readyForSaving={}",
                    holder.getPos(), roomChunks.contains(holder.getPos().toLong()), holder.getLatestStatus(), holder.getPersistedStatus(),
                    holder.getTicketLevel(), holder.getGenerationRefCount(), holder.getSaveSyncFuture().isDone(), holder.isReadyForSaving()));
        }
    }

    /** A copy of {@code ChunkMap.pendingUnloads}; read off-thread, so it retries if the map changes mid-copy. */
    @SuppressWarnings("unchecked")
    private static List<ChunkHolder> pendingUnloads(ChunkMap chunkMap) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Field field = ChunkMap.class.getDeclaredField("pendingUnloads");
                field.setAccessible(true);
                return List.copyOf(((Long2ObjectLinkedOpenHashMap<ChunkHolder>) field.get(chunkMap)).values());
            } catch (ReflectiveOperationException e) {
                TinyTunnels.LOGGER.warn("[TT-DEBUG] couldn't read ChunkMap.pendingUnloads", e);
                return List.of();
            } catch (RuntimeException e) {
                // Concurrent modification while copying; try again.
            }
        }
        return List.of();
    }

    private ShutdownWatchdog() {}
}
