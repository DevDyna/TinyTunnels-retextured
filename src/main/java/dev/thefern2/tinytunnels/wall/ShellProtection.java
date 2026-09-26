package dev.thefern2.tinytunnels.wall;

import java.util.ArrayList;
import java.util.List;

import dev.thefern2.tinytunnels.TinyTunnels;
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
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps room shells intact. Room and tunnel walls can't be broken by anyone, in any game mode.
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

    /** Runs a wall change made by this mod (building rooms, placing or removing tunnels). */
    public static void edit(Runnable change) {
        editDepth++;
        try {
            change.run();
        } finally {
            editDepth--;
        }
    }

    public static boolean isShellBlock(BlockState state) {
        return state.is(ModBlocks.ROOM_WALL.get()) || state.is(ModBlocks.TUNNEL_WALL.get());
    }

    @SubscribeEvent
    static void onBreak(BreakBlockEvent event) {
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
