package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Use on a room's wall from inside to turn it into a redstone tunnel mapped to the first free outer
 * face. It starts carrying the signal in, from outside the machine into the room.
 */
public class RedstoneTunnelItem extends Item {
    public RedstoneTunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return TunnelPlacement.place(context, ModTunnelKinds.REDSTONE.get(), ModTunnelKinds.REDSTONE.get().defaultData());
    }
}
