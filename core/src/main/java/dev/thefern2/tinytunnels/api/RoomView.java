package dev.thefern2.tinytunnels.api;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;

/** One room, as it is right now. A snapshot: read it right away, and ask again after a change. */
public interface RoomView {
    UUID id();

    /** The interior's width in blocks (odd, 1 to 13). */
    int size();

    RoomShape shape();

    /** Where the machine bound to this room stands; empty while the machine is an item. */
    Optional<GlobalPos> host();

    /** The tunnels by machine face. Only kinds that are registered; see {@link #isFaceUsed}. */
    Map<Direction, Tunnel<?>> tunnels();

    /**
     * True if the face has a tunnel, including one of a kind that isn't registered (its mod was removed). Such a
     * tunnel keeps its face and its data until the mod is back.
     */
    boolean isFaceUsed(Direction face);

    /** The tunnel whose wall is at {@code wall}. */
    Optional<Tunnel<?>> tunnelAt(BlockPos wall);

    /** The tunnel on {@code face}, if it's of {@code kind}. */
    <D> Optional<Tunnel<D>> tunnel(Direction face, TunnelKind<D> kind);
}
