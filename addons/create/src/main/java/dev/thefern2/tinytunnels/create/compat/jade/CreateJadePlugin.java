package dev.thefern2.tinytunnels.create.compat.jade;

import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelBlockEntity;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.create.port.KineticPortBlock;
import dev.thefern2.tinytunnels.create.port.KineticPortBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade tooltips for both ends of the kinetic tunnel: the wall inside the room and the port outside. Jade finds this
 * class through {@link WailaPlugin}; nothing else in the addon refers to this package, so it runs without Jade.
 */
@WailaPlugin
public class CreateJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(KineticTooltips.KINETIC_TUNNEL, KineticTunnelBlockEntity.class);
        registration.registerBlockDataProvider(KineticTooltips.KINETIC_PORT, KineticPortBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(KineticTooltips.KINETIC_TUNNEL, KineticTunnelWallBlock.class);
        registration.registerBlockComponent(KineticTooltips.KINETIC_PORT, KineticPortBlock.class);
    }
}
