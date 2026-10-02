package dev.thefern2.tinytunnels.api;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * One tunnel in a room, as it is right now. A snapshot: it doesn't follow later changes.
 *
 * @param face   the machine face this tunnel belongs to
 * @param wall   the tunnel wall's position in the room dimension
 * @param inward the direction from the wall into the room
 * @param data   the kind's per-tunnel data
 */
public record Tunnel<D>(TunnelKind<D> kind, Direction face, BlockPos wall, Direction inward, D data) {
    /** This tunnel typed as {@code kind}, or empty if it's another kind. */
    @SuppressWarnings("unchecked")
    public <E> Optional<Tunnel<E>> as(TunnelKind<E> kind) {
        return this.kind == kind ? Optional.of((Tunnel<E>) this) : Optional.empty();
    }
}
