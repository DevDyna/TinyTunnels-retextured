package dev.thefern2.tinytunnels.wall;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
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

    /** The wall block of a registered tunnel kind. Works on both sides: the kind registry is synced. */
    public static boolean isTunnelWall(BlockState state) {
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
    static void onShellRemoved(ServerLevel level, BlockPos pos, BlockState removed) {
        if (editDepth > 0 || !RoomDimension.isRoomLevel(level)) return;
        PENDING.add(new Repair(level, pos.immutable(), removed));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) return;
        List<Repair> repairs = List.copyOf(PENDING);
        PENDING.clear();
        for (Repair repair : repairs) {
            ServerLevel level = repair.level();
            if (!level.isLoaded(repair.pos()) || isShellBlock(level.getBlockState(repair.pos()))) continue;
            Room room = RoomData.get(level.getServer()).byChunk(new ChunkPos(repair.pos().getX() >> 4, repair.pos().getZ() >> 4)).orElse(null);
            if (room == null || !room.geometry().isShell(repair.pos())) continue;
            edit(() -> level.setBlock(repair.pos(), repair.state(), Block.UPDATE_ALL));
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    private ShellProtection() {}
}
