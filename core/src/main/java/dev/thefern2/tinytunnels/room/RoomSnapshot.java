package dev.thefern2.tinytunnels.room;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.thefern2.tinytunnels.api.RoomShape;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;

/** {@link RoomView} over one immutable {@link Room}: a snapshot, as the API promises. */
record RoomSnapshot(Room room) implements RoomView {
    @Override
    public UUID id() {
        return room.id();
    }

    @Override
    public int size() {
        return room.size();
    }

    @Override
    public RoomShape shape() {
        return room.geometry();
    }

    @Override
    public Optional<GlobalPos> host() {
        return room.host();
    }

    @Override
    public Map<Direction, Tunnel<?>> tunnels() {
        Map<Direction, Tunnel<?>> tunnels = new EnumMap<>(Direction.class);
        room.faces().forEach((face, tunnel) -> tunnels.put(face, room.toTunnel(face, tunnel)));
        return Collections.unmodifiableMap(tunnels);
    }

    @Override
    public boolean isFaceUsed(Direction face) {
        return room.isFaceUsed(face);
    }

    @Override
    public Optional<Tunnel<?>> tunnelAt(BlockPos wall) {
        Direction face = room.faceAt(wall);
        return face == null ? Optional.empty() : Optional.ofNullable(room.view(face));
    }

    @Override
    public <D> Optional<Tunnel<D>> tunnel(Direction face, TunnelKind<D> kind) {
        RoomTunnel<D> tunnel = room.tunnel(face, kind);
        return tunnel == null ? Optional.empty() : Optional.of(room.toTunnel(face, tunnel));
    }
}
