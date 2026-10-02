package dev.thefern2.tinytunnels.wall;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps room shells intact. Room and tunnel walls (every kind) can't be broken by anyone, in any game mode.
 * Anything else that removes a wall (commands, mods that set blocks directly) is undone on the next
 * tick. Blocks inside rooms are not affected.
 *
 * <p>Our own code changes walls through {@link #edit(Runnable)} so it isn't reverted.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class ShellProtection {
    private record Repair(ServerLevel level, BlockPos pos, BlockState state) {}

    private static final List<Repair> PENDING = new ArrayList<>();
    /** Room-dimension chunks that just loaded; their room's whole shell is repaired on the next tick. Loads can arrive off the main tick. */
    private static final Queue<ChunkPos> LOADED_ROOM_CHUNKS = new ConcurrentLinkedQueue<>();
    private static int editDepth;
    /** Kinds don't change after registration, so their walls are collected once. */
    private static volatile @Nullable Set<Block> tunnelWalls;

    /** Runs a wall change made by this mod (building rooms, placing or removing tunnels). */
    public static void edit(Runnable change) {
        editDepth++;
        try {
            change.run();
        } finally {
            editDepth--;
        }
    }

    /** A room wall, or the wall block of any registered tunnel kind. */
    public static boolean isShellBlock(BlockState state) {
        return state.is(ModBlocks.ROOM_WALL.get()) || isTunnelWall(state);
    }

    /**
     * The wall block of a registered tunnel kind, or the unknown tunnel wall. Works on both sides: the kind registry
     * is synced.
     */
    public static boolean isTunnelWall(BlockState state) {
        if (state.is(ModBlocks.UNKNOWN_TUNNEL_WALL.get())) return true;
        Set<Block> walls = tunnelWalls;
        if (walls == null) {
            walls = TunnelKinds.REGISTRY.stream().map(TunnelKind::wallBlock).collect(Collectors.toUnmodifiableSet());
            tunnelWalls = walls;
        }
        return walls.contains(state.getBlock());
    }

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        if (isShellBlock(event.getState())) event.setCanceled(true);
    }

    /** Called when a shell block was removed or replaced. Schedules putting it back unless we did it. */
    public static void onShellRemoved(ServerLevel level, BlockPos pos, BlockState removed) {
        if (editDepth > 0 || !RoomDimension.isRoomLevel(level)) return;
        PENDING.add(new Repair(level, pos.immutable(), removed));
    }

    /**
     * A room's chunk loaded: repair its whole shell next tick, as entering does. A wall can be gone without anything
     * removing it: a tunnel wall whose block isn't registered any more (its addon was removed) loads as air. Entering
     * repairs that, but a player can already be inside when the world loads (saved and quit in the room), and
     * capabilities or redstone through the hole would be wrong until someone enters.
     */
    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && RoomDimension.isRoomLevel(level)) LOADED_ROOM_CHUNKS.add(event.getChunk().getPos());
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        repairLoadedRooms(event.getServer());
        if (PENDING.isEmpty()) return;
        List<Repair> repairs = List.copyOf(PENDING);
        PENDING.clear();
        for (Repair repair : repairs) {
            ServerLevel level = repair.level();
            if (!level.isLoaded(repair.pos()) || isShellBlock(level.getBlockState(repair.pos()))) continue;
            Room room = RoomData.get(level.getServer()).byChunk(new ChunkPos(repair.pos().getX() >> 4, repair.pos().getZ() >> 4)).orElse(null);
            if (room == null || !room.geometry().isShell(repair.pos())) continue;
            BlockState state = repairState(room, repair);
            edit(() -> level.setBlock(repair.pos(), state, Block.UPDATE_ALL));
        }
    }

    /**
     * What goes back: a tunnel's wall from the room data through its kind (an addon's wall too, whatever state it
     * was left in), the unknown wall for a tunnel of an unregistered kind, otherwise the block that was removed.
     */
    private static BlockState repairState(Room room, Repair repair) {
        Direction face = room.faceAt(repair.pos());
        Direction inward = room.geometry().inwardNormal(repair.pos());
        if (face != null && inward != null) return wallState(room.at(face), face, inward);
        if (room.unknownFaceAt(repair.pos()) != null) return ModBlocks.UNKNOWN_TUNNEL_WALL.get().defaultBlockState();
        return repair.state();
    }

    private static <D> BlockState wallState(RoomTunnel<D> tunnel, Direction face, Direction inward) {
        return tunnel.kind().wallState(face, inward, tunnel.data());
    }

    private static void repairLoadedRooms(MinecraftServer server) {
        if (LOADED_ROOM_CHUNKS.isEmpty()) return;
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        RoomData data = RoomData.get(server);
        for (ChunkPos chunk; (chunk = LOADED_ROOM_CHUNKS.poll()) != null; ) {
            if (rooms == null) continue;
            Room room = data.byChunk(chunk).orElse(null);
            // Only while the chunk is still loaded: build() must never load one.
            if (room != null && rooms.hasChunk(chunk.x, chunk.z)) RoomBuilder.build(rooms, room);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        LOADED_ROOM_CHUNKS.clear();
    }

    private ShellProtection() {}
}
