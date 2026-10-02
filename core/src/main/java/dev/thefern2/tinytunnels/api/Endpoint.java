package dev.thefern2.tinytunnels.api;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * The block on the other side of a tunnel, from {@link TunnelService#otherSide}: look a capability up at
 * {@code pos}, from {@code side}.
 *
 * @param side the side of the block at {@code pos} that faces the tunnel
 */
public record Endpoint(ServerLevel level, BlockPos pos, Direction side) {}
