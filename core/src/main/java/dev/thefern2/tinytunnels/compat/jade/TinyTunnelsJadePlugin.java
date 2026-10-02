package dev.thefern2.tinytunnels.compat.jade;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineBlockEntity;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelBlockEntity;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.TunnelBlockEntity;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade tooltips for machines and core's tunnel walls. Addon kinds bring their own Jade plugin for their walls. Jade finds this class through
 * {@link WailaPlugin}; nothing else in the mod refers to this package, so Tiny Tunnels runs without Jade.
 */
@WailaPlugin
public class TinyTunnelsJadePlugin implements IWailaPlugin {
    static final ResourceLocation MACHINE = TinyTunnels.id("machine");
    static final ResourceLocation TUNNEL = TinyTunnels.id("tunnel");
    static final ResourceLocation REDSTONE_TUNNEL = TinyTunnels.id("redstone_tunnel");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ServerData.MACHINE, MachineBlockEntity.class);
        registration.registerBlockDataProvider(ServerData.REDSTONE_TUNNEL, RedstoneTunnelBlockEntity.class);
        registration.registerBlockDataProvider(ServerData.TUNNEL, TunnelBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(Tooltips.MACHINE, MachineBlock.class);
        registration.registerBlockComponent(Tooltips.TUNNEL, TunnelWallBlock.class);
        registration.registerBlockComponent(Tooltips.REDSTONE_TUNNEL, RedstoneTunnelWallBlock.class);
    }
}
