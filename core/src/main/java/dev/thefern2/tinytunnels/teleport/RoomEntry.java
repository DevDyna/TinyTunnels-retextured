package dev.thefern2.tinytunnels.teleport;

import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

import dev.thefern2.tinytunnels.room.EntryPoint;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Where a player appears inside a room. Rooms fill up, so a fixed spot could put them inside
 * blocks. Instead each room remembers where the last player stood when they left with the Shrinker,
 * and entry starts there, as long as it's still clear. Otherwise the floor centre, otherwise the
 * clear spot nearest to it, preferring one with something to stand on.
 */
public final class RoomEntry {
    /** Remembers {@code pos} as the room's entry point, if it's inside a room's interior. */
    public static void rememberExit(ServerLevel level, Vec3 pos, float yRot, float xRot) {
        if (!RoomDimension.isRoomLevel(level)) return;
        BlockPos block = BlockPos.containing(pos);
        RoomData data = RoomData.get(level.getServer());
        Room room = data.byChunk(new ChunkPos(block.getX() >> 4, block.getZ() >> 4)).orElse(null);
        if (room != null && room.geometry().isInterior(block)) data.setEntry(room.id(), new EntryPoint(pos, yRot, xRot));
    }

    /**
     * Where a body of {@code size} should enter {@code room}. Keeps the saved facing with the saved
     * spot; otherwise uses {@code yRot}/{@code xRot}. Falls back to the floor centre if nothing is clear.
     */
    public static EntryPoint find(ServerLevel rooms, Room room, EntityDimensions size, float yRot, float xRot) {
        if (room.entry().isPresent() && fits(rooms, room, room.entry().get().pos(), size)) return room.entry().get();
        RoomGeometry geometry = room.geometry();
        Vec3 spawn = geometry.spawn();
        if (fits(rooms, room, spawn, size)) return new EntryPoint(spawn, yRot, xRot);

        List<Vec3> clear = StreamSupport.stream(BlockPos.betweenClosed(geometry.min(), geometry.max()).spliterator(), false)
                .filter(geometry::isInterior)
                .map(BlockPos::getBottomCenter)
                .filter(pos -> fits(rooms, room, pos, size))
                .sorted(Comparator.comparingDouble(pos -> pos.distanceToSqr(spawn)))
                .toList();
        Vec3 best = clear.stream().filter(pos -> standsOnSomething(rooms, pos)).findFirst()
                .orElse(clear.isEmpty() ? spawn : clear.getFirst());
        return new EntryPoint(best, yRot, xRot);
    }

    /** Inside the room, and the body doesn't overlap any block (or blocking entity). */
    public static boolean fits(ServerLevel rooms, Room room, Vec3 pos, EntityDimensions size) {
        return room.geometry().isInterior(BlockPos.containing(pos)) && rooms.noCollision(size.makeBoundingBox(pos));
    }

    private static boolean standsOnSomething(ServerLevel rooms, Vec3 pos) {
        BlockPos below = BlockPos.containing(pos).below();
        return !rooms.getBlockState(below).getCollisionShape(rooms, below).isEmpty();
    }

    private RoomEntry() {}
}
