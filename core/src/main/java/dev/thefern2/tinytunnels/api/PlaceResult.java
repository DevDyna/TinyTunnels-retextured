package dev.thefern2.tinytunnels.api;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** What {@link TunnelService#place} did. */
public sealed interface PlaceResult {
    /** The tunnel is in, on {@code face}. */
    record Placed(Direction face) implements PlaceResult {}

    /** Nothing changed; {@code reason} says why, for the player. */
    record Refused(Component reason) implements PlaceResult {}
}
