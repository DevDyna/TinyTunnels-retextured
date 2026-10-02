package dev.thefern2.tinytunnels.room;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One tunnel as a room keeps it: its kind, its wall in the room dimension, and the kind's data. The machine face
 * is the room's map key; the inward direction comes from the room's geometry. Saved as
 * {@code {"kind": "tinytunnels:transfer", "wall": [x, y, z], "data": ...}}, the data with the kind's codec.
 */
public record RoomTunnel<D>(TunnelKind<D> kind, BlockPos wall, D data) {
    /** Unknown kinds fail to decode for now (A5 keeps them as raw entries). */
    public static final Codec<RoomTunnel<?>> CODEC = TunnelKinds.REGISTRY.byNameCodec().dispatch("kind", RoomTunnel::kind, RoomTunnel::codecFor);

    public RoomTunnel {
        wall = wall.immutable();
    }

    private static <D> MapCodec<RoomTunnel<D>> codecFor(TunnelKind<D> kind) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.fieldOf("wall").forGetter(RoomTunnel::wall),
                kind.dataCodec().fieldOf("data").forGetter(RoomTunnel::data)
        ).apply(i, (wall, data) -> new RoomTunnel<>(kind, wall, data)));
    }

    public RoomTunnel<D> withData(D data) {
        return new RoomTunnel<>(kind, wall, data);
    }

    /** This entry typed as {@code kind}, or empty if it's another kind. */
    @SuppressWarnings("unchecked")
    public <E> Optional<RoomTunnel<E>> as(TunnelKind<E> kind) {
        return this.kind == kind ? Optional.of((RoomTunnel<E>) this) : Optional.empty();
    }

    /** The public view of this tunnel on {@code face}. */
    public Tunnel<D> toTunnel(Direction face, Direction inward) {
        return new Tunnel<>(kind, face, wall, inward, data);
    }
}
