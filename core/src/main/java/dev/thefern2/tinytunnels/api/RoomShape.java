package dev.thefern2.tinytunnels.api;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where a room sits in the room dimension: a cube of walls (the shell) around the interior. Positions are in the
 * room dimension.
 */
public interface RoomShape {
    /** True for any block of the room, shell or interior. */
    boolean contains(BlockPos pos);

    boolean isInterior(BlockPos pos);

    boolean isShell(BlockPos pos);

    /** The middle block of the wall, floor or ceiling on {@code side}. */
    BlockPos wallCenter(Direction side);

    /**
     * For a shell block that can hold a tunnel (not an edge or corner), the direction pointing into the room;
     * otherwise null.
     */
    @Nullable Direction inwardNormal(BlockPos pos);
}
