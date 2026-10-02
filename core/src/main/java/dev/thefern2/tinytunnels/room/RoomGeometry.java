package dev.thefern2.tinytunnels.room;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.RoomShape;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Where a room sits in the room dimension. Rooms live on a grid, one room per chunk, four chunks
 * apart. A forced (entity-ticking) room chunk fully loads every chunk within 2 of it; rooms must be
 * at least 3 apart so one room's ticket never loads a neighbour's machines, which could otherwise
 * keep each other loaded forever. Each room is a cube of
 * {@code interior + 2} blocks (walls included), centred in its chunk, floor at {@link #FLOOR_Y}.
 */
public final class RoomGeometry implements RoomShape {
    public static final int FLOOR_Y = 64;
    public static final int GRID_WIDTH = 256;
    public static final int GRID_SPACING = 4;
    public static final int MAX_INTERIOR = 13;

    private final int gridIndex;
    private final int interior;
    private final int chunkX;
    private final int chunkZ;
    private final BlockPos min;
    private final BlockPos max;

    public RoomGeometry(int gridIndex, int interior) {
        if (gridIndex < 0) throw new IllegalArgumentException("gridIndex must be >= 0: " + gridIndex);
        if (interior < 1 || interior > MAX_INTERIOR || interior % 2 == 0) {
            throw new IllegalArgumentException("interior must be odd and between 1 and " + MAX_INTERIOR + ": " + interior);
        }
        this.gridIndex = gridIndex;
        this.interior = interior;
        this.chunkX = (gridIndex % GRID_WIDTH) * GRID_SPACING;
        this.chunkZ = (gridIndex / GRID_WIDTH) * GRID_SPACING;
        int outer = interior + 2;
        int offset = (16 - outer) / 2;
        this.min = new BlockPos(chunkX * 16 + offset, FLOOR_Y, chunkZ * 16 + offset);
        this.max = min.offset(outer - 1, outer - 1, outer - 1);
    }

    /** The grid index whose room lives in the given chunk, or -1 if no room can be there. */
    public static int gridIndexOf(int chunkX, int chunkZ) {
        if (chunkX < 0 || chunkZ < 0 || chunkX % GRID_SPACING != 0 || chunkZ % GRID_SPACING != 0) return -1;
        int column = chunkX / GRID_SPACING;
        if (column >= GRID_WIDTH) return -1;
        return (chunkZ / GRID_SPACING) * GRID_WIDTH + column;
    }

    public int gridIndex() {
        return gridIndex;
    }

    public int interior() {
        return interior;
    }

    public ChunkPos chunk() {
        return new ChunkPos(chunkX, chunkZ);
    }

    /** Lowest corner of the shell (a wall block). */
    public BlockPos min() {
        return min;
    }

    /** Highest corner of the shell (a wall block). */
    public BlockPos max() {
        return max;
    }

    /** Where a player appears when entering: the floor centre, standing on the floor. */
    public Vec3 spawn() {
        double half = interior / 2.0;
        return new Vec3(min.getX() + 1 + half, FLOOR_Y + 1, min.getZ() + 1 + half);
    }

    @Override
    public boolean contains(BlockPos pos) {
        return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    @Override
    public boolean isInterior(BlockPos pos) {
        return contains(pos) && boundaryPlanes(pos) == 0;
    }

    @Override
    public boolean isShell(BlockPos pos) {
        return contains(pos) && boundaryPlanes(pos) > 0;
    }

    /** Edges and corners of the shell: they touch more than one side, so they can't hold a tunnel. */
    public boolean isEdgeOrCorner(BlockPos pos) {
        return contains(pos) && boundaryPlanes(pos) > 1;
    }

    /** The middle block of the wall (or floor/ceiling) on {@code side}. */
    @Override
    public BlockPos wallCenter(Direction side) {
        int half = interior / 2;
        return min.offset(1 + half, 1 + half, 1 + half).relative(side, half + 1);
    }

    /** For a wall block in the middle of a face, the direction pointing into the room; otherwise null. */
    @Override
    public @Nullable Direction inwardNormal(BlockPos pos) {
        if (!contains(pos) || boundaryPlanes(pos) != 1) return null;
        if (pos.getX() == min.getX()) return Direction.EAST;
        if (pos.getX() == max.getX()) return Direction.WEST;
        if (pos.getY() == min.getY()) return Direction.UP;
        if (pos.getY() == max.getY()) return Direction.DOWN;
        if (pos.getZ() == min.getZ()) return Direction.SOUTH;
        return Direction.NORTH;
    }

    private int boundaryPlanes(BlockPos pos) {
        int planes = 0;
        if (pos.getX() == min.getX() || pos.getX() == max.getX()) planes++;
        if (pos.getY() == min.getY() || pos.getY() == max.getY()) planes++;
        if (pos.getZ() == min.getZ() || pos.getZ() == max.getZ()) planes++;
        return planes;
    }
}
