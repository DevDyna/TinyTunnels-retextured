package dev.thefern2.tinytunnels.room;

import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Places a room's walls in the room dimension. */
public final class RoomBuilder {
    /**
     * Repairs a room's shell: every wall block is put back, and every tunnel is restored at its
     * mapped position. Safe to call on an intact room.
     */
    public static void build(ServerLevel rooms, Room room) {
        ShellProtection.edit(() -> {
            buildShell(rooms, room.geometry());
            room.tunnels().forEach((face, pos) -> {
                Direction inward = room.geometry().inwardNormal(pos);
                if (inward == null) return;
                BlockState tunnel = ModBlocks.TUNNEL_WALL.get().defaultBlockState()
                        .setValue(TunnelWallBlock.FACE, face).setValue(TunnelWallBlock.INWARD, inward);
                if (!rooms.getBlockState(pos).equals(tunnel)) rooms.setBlock(pos, tunnel, Block.UPDATE_ALL);
            });
        });
    }

    /** Builds a fresh room: the shell plus an empty inside. */
    public static void buildNew(ServerLevel rooms, RoomGeometry room) {
        ShellProtection.edit(() -> {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : BlockPos.betweenClosed(room.min(), room.max())) {
                if (room.isInterior(pos)) {
                    rooms.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
            buildShell(rooms, room);
        });
    }

    private static void buildShell(ServerLevel rooms, RoomGeometry room) {
        BlockState wall = ModBlocks.ROOM_WALL.get().defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(room.min(), room.max())) {
            if (room.isShell(pos)) {
                BlockState current = rooms.getBlockState(pos);
                // Tunnels are put back by build(Room); leave any that are already there.
                if (!current.is(ModBlocks.ROOM_WALL.get()) && !current.is(ModBlocks.TUNNEL_WALL.get())) {
                    rooms.setBlock(pos, wall, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private RoomBuilder() {}
}
