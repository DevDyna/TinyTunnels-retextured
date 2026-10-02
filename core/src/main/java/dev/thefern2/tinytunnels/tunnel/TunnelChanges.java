package dev.thefern2.tinytunnels.tunnel;

import java.util.Objects;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.PlacementContext;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelContext;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.event.TunnelEvent;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Every tunnel change, for every kind: placing, moving to another face, removing and changing data. Each one
 * updates the wall and {@link RoomData}, does the follow-ups (capability refresh, machine face looks, the core
 * kinds' own updates through {@link CoreKind}) and then posts the {@link TunnelEvent}. The wrench, the tunnel
 * items, the wall clicks and the public {@code TunnelService} all come through here.
 */
public final class TunnelChanges {
    /** The room whose space contains {@code pos} in the room dimension. */
    public static @Nullable Room roomAt(ServerLevel rooms, BlockPos pos) {
        return RoomData.get(rooms.getServer()).byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4))
                .filter(room -> room.geometry().contains(pos)).orElse(null);
    }

    /**
     * Places a tunnel at a room wall, on the first free face. Core's checks in order: the room dimension, a plain
     * room wall of a room, not an edge or corner, a free face, the kind's {@code maxPerRoom}, then the kind's own
     * {@code refusePlacement}. Nothing changes on a refusal.
     */
    public static <D> PlaceResult place(ServerLevel rooms, BlockPos wall, TunnelKind<D> kind, D data, @Nullable Player player) {
        Room room = RoomDimension.isRoomLevel(rooms) ? roomAt(rooms, wall) : null;
        if (room == null || !room.geometry().isShell(wall) || !rooms.getBlockState(wall).is(ModBlocks.ROOM_WALL.get())) {
            return new PlaceResult.Refused(Component.translatableWithFallback("message.tinytunnels.tunnel.not_room_wall",
                    "Tunnels go on a room's wall, from inside"));
        }
        Direction inward = room.geometry().inwardNormal(wall);
        if (inward == null) return new PlaceResult.Refused(Component.translatable("message.tinytunnels.tunnel.edge"));
        Direction face = room.nextFreeFace(null);
        if (face == null) return new PlaceResult.Refused(Component.translatable("message.tinytunnels.tunnel.all_faces_used"));
        if (room.count(kind) >= kind.maxPerRoom()) {
            return new PlaceResult.Refused(kind.maxPerRoom() == 1
                    ? Component.translatableWithFallback("message.tinytunnels.tunnel.one_per_machine", "This machine already has a %s", kind.displayName())
                    : Component.translatableWithFallback("message.tinytunnels.tunnel.max_per_room", "This machine already has %2$s of: %1$s",
                    kind.displayName(), kind.maxPerRoom()));
        }
        Component refusal = kind.refusePlacement(new PlacementContext<>(rooms, room.view(), wall.immutable(), face, inward, data, player));
        if (refusal != null) return new PlaceResult.Refused(refusal);

        RoomData roomData = RoomData.get(rooms.getServer());
        ShellProtection.edit(() -> rooms.setBlock(wall, kind.wallState(face, inward, data), Block.UPDATE_ALL));
        roomData.setTunnel(room.id(), face, new RoomTunnel<>(kind, wall, data));
        Room after = roomData.room(room.id()).orElseThrow();
        refresh(rooms, kind, room, after);
        if (kind instanceof CoreKind<?> core) core.added(rooms.getServer(), room.id(), face);
        post(new TunnelEvent.Added(rooms, current(rooms, room.id()).view(), current(rooms, room.id()).view(face)));
        return new PlaceResult.Placed(face);
    }

    /**
     * Puts a copy of {@code tunnel} into another room, on the same face and the same wall position relative to the
     * room: for copying a room. No placement checks (the room is new and has the same shape); data goes through the
     * core kinds' {@code movedData}, so nothing transient (redstone power) is copied. Then a placement's follow-ups
     * and {@link TunnelEvent.Added}.
     */
    public static <D> void copyInto(ServerLevel rooms, Room from, UUID roomId, Direction face, RoomTunnel<D> tunnel) {
        Room room = current(rooms, roomId);
        BlockPos wall = room.geometry().min().offset(tunnel.wall().subtract(from.geometry().min()));
        Direction inward = room.geometry().inwardNormal(wall);
        if (inward == null) return;
        TunnelKind<D> kind = tunnel.kind();
        D data = kind instanceof CoreKind<?> core ? movedData(core, tunnel.data()) : tunnel.data();
        ShellProtection.edit(() -> rooms.setBlock(wall, kind.wallState(face, inward, data), Block.UPDATE_ALL));
        RoomData.get(rooms.getServer()).setTunnel(roomId, face, new RoomTunnel<>(kind, wall, data));
        refresh(rooms, kind, room, current(rooms, roomId));
        if (kind instanceof CoreKind<?> core) core.added(rooms.getServer(), roomId, face);
        post(new TunnelEvent.Added(rooms, current(rooms, roomId).view(), current(rooms, roomId).view(face)));
    }

    /** Moves the tunnel on {@code face} to {@code newFace}: same wall block (so its block entity stays), same data. */
    public static boolean move(ServerLevel rooms, UUID roomId, Direction face, Direction newFace) {
        Room room = RoomData.get(rooms.getServer()).room(roomId).orElse(null);
        RoomTunnel<?> tunnel = room == null ? null : room.at(face);
        if (tunnel == null || face == newFace || room.isFaceUsed(newFace)) return false;
        moveTyped(rooms, room, face, newFace, tunnel);
        return true;
    }

    private static <D> void moveTyped(ServerLevel rooms, Room room, Direction face, Direction newFace, RoomTunnel<D> tunnel) {
        TunnelKind<D> kind = tunnel.kind();
        D data = kind instanceof CoreKind<?> core ? movedData(core, tunnel.data()) : tunnel.data();
        RoomData roomData = RoomData.get(rooms.getServer());
        Direction inward = room.geometry().inwardNormal(tunnel.wall());
        if (inward != null && rooms.isLoaded(tunnel.wall())) {
            ShellProtection.edit(() -> rooms.setBlock(tunnel.wall(), kind.wallState(newFace, inward, data), Block.UPDATE_ALL));
        }
        roomData.moveTunnel(room.id(), face, newFace);
        roomData.setData(room.id(), newFace, kind, data);
        Room after = current(rooms, room.id());
        refresh(rooms, kind, room, after);
        if (kind instanceof CoreKind<?> core) core.moved(rooms.getServer(), room.id(), face, newFace);
        Room posted = current(rooms, room.id());
        post(new TunnelEvent.Moved(rooms, posted.view(), Objects.requireNonNull(posted.view(newFace)), face));
    }

    /**
     * Moves every tunnel of the room to {@code turn.apply(face)} at once, for turning a machine: a plain {@link #move}
     * can't, since each move needs a free face. Each moved tunnel gets what a move gives it (its wall's state, the
     * core kinds' {@code movedData} and {@code moved}, a {@link TunnelEvent.Moved}), posted after all have moved.
     * {@code turn} must be one-to-one.
     */
    public static void turn(ServerLevel rooms, UUID roomId, UnaryOperator<Direction> turn) {
        RoomData roomData = RoomData.get(rooms.getServer());
        Room before = roomData.room(roomId).orElse(null);
        if (before == null) return;
        roomData.turnFaces(roomId, turn);
        before.faces().forEach((face, tunnel) -> {
            if (turn.apply(face) != face) turnWall(rooms, before, turn.apply(face), tunnel);
        });
        Room after = current(rooms, roomId);
        CapabilityUpdates.roomChanged(rooms.getServer(), before);
        CapabilityUpdates.roomChanged(rooms.getServer(), after);
        before.faces().forEach((face, tunnel) -> {
            Direction newFace = turn.apply(face);
            if (newFace == face) return;
            if (tunnel.kind() instanceof CoreKind<?> core) core.moved(rooms.getServer(), roomId, face, newFace);
        });
        Room posted = current(rooms, roomId);
        before.faces().forEach((face, tunnel) -> {
            Direction newFace = turn.apply(face);
            if (newFace != face) post(new TunnelEvent.Moved(rooms, posted.view(), Objects.requireNonNull(posted.view(newFace)), face));
        });
    }

    /** One tunnel's part of {@link #turn}: its moved data and its wall's state for the new face. */
    private static <D> void turnWall(ServerLevel rooms, Room room, Direction newFace, RoomTunnel<D> tunnel) {
        TunnelKind<D> kind = tunnel.kind();
        D data = kind instanceof CoreKind<?> core ? movedData(core, tunnel.data()) : tunnel.data();
        RoomData.get(rooms.getServer()).setData(room.id(), newFace, kind, data);
        Direction inward = room.geometry().inwardNormal(tunnel.wall());
        if (inward != null && rooms.isLoaded(tunnel.wall())) {
            ShellProtection.edit(() -> rooms.setBlock(tunnel.wall(), kind.wallState(newFace, inward, data), Block.UPDATE_ALL));
        }
    }

    @SuppressWarnings("unchecked")
    private static <D> D movedData(CoreKind<?> core, D data) {
        return ((CoreKind<D>) core).movedData(data);
    }

    /**
     * Removes the tunnel on {@code face}: the kind's {@code onRemoving}, the wall back to a room wall, the room
     * data. Doesn't ask {@code confirmRemoval} or give the item back; the wrench does both.
     */
    public static boolean remove(ServerLevel rooms, UUID roomId, Direction face, @Nullable Player player) {
        Room room = RoomData.get(rooms.getServer()).room(roomId).orElse(null);
        RoomTunnel<?> tunnel = room == null ? null : room.at(face);
        if (tunnel == null) return false;
        removeTyped(rooms, room, face, tunnel, player);
        return true;
    }

    private static <D> void removeTyped(ServerLevel rooms, Room room, Direction face, RoomTunnel<D> tunnel, @Nullable Player player) {
        TunnelKind<D> kind = tunnel.kind();
        Tunnel<D> view = room.toTunnel(face, tunnel);
        if (rooms.isLoaded(tunnel.wall())) {
            kind.onRemoving(new TunnelContext<>(rooms, room.view(), view), player);
            ShellProtection.edit(() -> rooms.setBlock(tunnel.wall(), ModBlocks.ROOM_WALL.get().defaultBlockState(), Block.UPDATE_ALL));
        }
        RoomData.get(rooms.getServer()).removeTunnel(room.id(), face);
        Room after = current(rooms, room.id());
        // The room as it was too, so the removed wall's position is notified.
        refresh(rooms, kind, room, after);
        if (kind instanceof CoreKind<?> core) core.removed(rooms.getServer(), room.id(), face);
        post(new TunnelEvent.Removed(rooms, current(rooms, room.id()).view(), view));
    }

    /**
     * Changes a tunnel's data: the room data, the wall's state if {@link TunnelKind#wallState} changed, the
     * follow-ups and {@link TunnelEvent.DataChanged}. False if there's no tunnel of {@code kind} on {@code face}.
     */
    public static <D> boolean setData(ServerLevel rooms, UUID roomId, Direction face, TunnelKind<D> kind, D data) {
        Room room = RoomData.get(rooms.getServer()).room(roomId).orElse(null);
        RoomTunnel<D> tunnel = room == null ? null : room.tunnel(face, kind);
        if (tunnel == null) return false;
        D old = tunnel.data();
        RoomData.get(rooms.getServer()).setData(roomId, face, kind, data);
        Direction inward = room.geometry().inwardNormal(tunnel.wall());
        if (inward != null && rooms.isLoaded(tunnel.wall())) {
            BlockState state = kind.wallState(face, inward, data);
            if (rooms.getBlockState(tunnel.wall()) != state) {
                ShellProtection.edit(() -> rooms.setBlock(tunnel.wall(), state, Block.UPDATE_ALL));
            }
        }
        refresh(rooms, kind, room, current(rooms, roomId));
        if (kind instanceof CoreKind<?> core) dataChanged(core, rooms, roomId, face, old, data);
        post(new TunnelEvent.DataChanged(rooms, current(rooms, roomId).view(), Objects.requireNonNull(current(rooms, roomId).view(face)), old));
        return true;
    }

    @SuppressWarnings("unchecked")
    private static <D> void dataChanged(CoreKind<?> core, ServerLevel rooms, UUID roomId, Direction face, D old, D data) {
        ((CoreKind<D>) core).dataChanged(rooms.getServer(), roomId, face, old, data);
    }

    /**
     * Only records new data and posts {@link TunnelEvent.DataChanged}: no wall update, no follow-ups. For a core kind
     * that keeps its own wall and machine in step, such as redstone power, which changes often.
     */
    static <D> void recordData(ServerLevel rooms, UUID roomId, Direction face, TunnelKind<D> kind, D data) {
        Room room = RoomData.get(rooms.getServer()).room(roomId).orElse(null);
        RoomTunnel<D> tunnel = room == null ? null : room.tunnel(face, kind);
        if (tunnel == null || tunnel.data().equals(data)) return;
        RoomData.get(rooms.getServer()).setData(roomId, face, kind, data);
        Room after = current(rooms, roomId);
        post(new TunnelEvent.DataChanged(rooms, after.view(), Objects.requireNonNull(after.view(face)), tunnel.data()));
    }

    /**
     * What the player sees after placing a tunnel, or after the wrench moved it to {@code face}: the kind's
     * {@link TunnelKind#placedMessage}, or core's generic one.
     */
    public static <D> Component placedMessage(TunnelKind<D> kind, Direction face, D data) {
        Component message = kind.placedMessage(face, data);
        return message != null ? message : Component.translatable("message.tinytunnels.tunnel.mapped", TunnelWallBlock.faceName(face));
    }

    /** Pipes look again on both ends (kinds with capabilities), or just the machine's face looks resync. */
    private static void refresh(ServerLevel rooms, TunnelKind<?> kind, Room before, Room after) {
        if (kind instanceof CoreKind<?> core && !core.hasCapabilities()) {
            CapabilityUpdates.syncFaces(after.id());
            return;
        }
        CapabilityUpdates.roomChanged(rooms.getServer(), before);
        CapabilityUpdates.roomChanged(rooms.getServer(), after);
    }

    private static Room current(ServerLevel rooms, UUID roomId) {
        return RoomData.get(rooms.getServer()).room(roomId).orElseThrow();
    }

    private static void post(TunnelEvent event) {
        NeoForge.EVENT_BUS.post(event);
    }

    private TunnelChanges() {}
}
