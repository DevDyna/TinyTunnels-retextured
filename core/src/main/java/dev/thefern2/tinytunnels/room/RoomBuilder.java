package dev.thefern2.tinytunnels.room;

import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.registry.ModBlocks;
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
     * Repairs a room's shell: every wall block is put back, and every tunnel (of every kind) is
     * restored at its mapped position from its kind's wall state. Safe to call on an intact room; runs on every entry.
     */
    public static void build(ServerLevel rooms, Room room) {
        ShellProtection.edit(() -> {
            buildShell(rooms, room.geometry());
            room.faces().forEach((face, tunnel) -> restore(rooms, room, face, tunnel));
            room.unknown().values().forEach(tunnel -> restoreUnknown(rooms, room, tunnel));
        });
    }

    /**
     * Puts one tunnel's wall back from its kind's {@code wallState}. A wall already in that state is left alone,
     * and a wall of the same block keeps its block entity (a buffer, an addon's own state).
     */
    private static <D> void restore(ServerLevel rooms, Room room, Direction face, RoomTunnel<D> tunnel) {
        Direction inward = room.geometry().inwardNormal(tunnel.wall());
        if (inward == null) return;
        BlockState state = tunnel.kind().wallState(face, inward, tunnel.data());
        if (rooms.getBlockState(tunnel.wall()) != state) rooms.setBlock(tunnel.wall(), state, Block.UPDATE_ALL);
    }

    /**
     * A tunnel kept as saved keeps its place with the inert unknown wall. If its kind is registered but couldn't
     * read the data, a wall of that kind is left alone instead, so its block entity (a buffer, say) survives.
     */
    private static void restoreUnknown(ServerLevel rooms, Room room, UnknownTunnel tunnel) {
        if (room.geometry().inwardNormal(tunnel.wall()) == null) return;
        BlockState current = rooms.getBlockState(tunnel.wall());
        TunnelKind<?> kind = TunnelKinds.REGISTRY.get(tunnel.kind());
        if (kind != null && current.is(kind.wallBlock())) return;
        BlockState unknown = ModBlocks.UNKNOWN_TUNNEL_WALL.get().defaultBlockState();
        if (current != unknown) rooms.setBlock(tunnel.wall(), unknown, Block.UPDATE_ALL);
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
                if (!ShellProtection.isShellBlock(current)) {
                    rooms.setBlock(pos, wall, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private RoomBuilder() {}
}
