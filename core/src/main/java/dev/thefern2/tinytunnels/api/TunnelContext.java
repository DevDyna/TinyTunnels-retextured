package dev.thefern2.tinytunnels.api;

import net.minecraft.server.level.ServerLevel;

/**
 * A tunnel handed to a {@link TunnelKind} hook, with where it is.
 *
 * @param level the room dimension
 */
public record TunnelContext<D>(ServerLevel level, RoomView room, Tunnel<D> tunnel) {}
