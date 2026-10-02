package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.room.RedstoneMode;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** The kinetic tunnel's messages. Its editing goes through {@link KineticTunnelKind} and {@link TunnelChanges}. */
public final class KineticTunnels {
    public static Component modeMessage(RedstoneMode mode, Direction face) {
        return Component.translatable("message.tinytunnels.kinetic_tunnel." + mode.getSerializedName(), TunnelWallBlock.faceName(face));
    }

    private KineticTunnels() {}
}
