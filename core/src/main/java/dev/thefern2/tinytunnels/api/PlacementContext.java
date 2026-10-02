package dev.thefern2.tinytunnels.api;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * A tunnel about to be placed, for {@link TunnelKind#refusePlacement}. Core's own checks have passed.
 *
 * @param level  the room dimension
 * @param wall   the room wall that will become the tunnel wall
 * @param face   the machine face the tunnel will get (the first free one)
 * @param inward the direction from the wall into the room
 * @param player who is placing it, or null when it isn't a player
 */
public record PlacementContext<D>(ServerLevel level, RoomView room, BlockPos wall, Direction face, Direction inward,
                                  D data, @Nullable Player player) {}
