package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.TunnelMode;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Use on a room's wall from inside to turn it into a tunnel mapped to the first free outer face. Starts pass-through. */
public class TunnelItem extends Item {
    public TunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return TunnelPlacement.place(context, ModTunnelKinds.TRANSFER.get(), TunnelMode.PASSTHROUGH);
    }
}
