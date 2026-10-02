package dev.thefern2.tinytunnels.compat.create;

import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.tunnel.TunnelPlacement;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Turns a room wall into a kinetic tunnel, mapped to the next free machine face, carrying rotation out
 * by default. One per machine ({@code KineticTunnelKind.maxPerRoom}): the machine is a single Create block, so a
 * second kinetic face would join two networks.
 */
public class KineticTunnelItem extends Item {
    public KineticTunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return TunnelPlacement.place(context, ModTunnelKinds.KINETIC.get(), RedstoneMode.OUT);
    }
}
