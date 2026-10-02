package dev.thefern2.tinytunnels.registry;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelKind;
import dev.thefern2.tinytunnels.tunnel.TransferTunnelKind;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/** Core's own tunnel kinds, registered through the public API the same way an addon's are. */
public final class ModTunnelKinds {
    public static final DeferredRegister<TunnelKind<?>> KINDS = DeferredRegister.create(TunnelKinds.REGISTRY_KEY, TinyTunnels.MODID);

    /** Items, fluids and energy. */
    public static final DeferredHolder<TunnelKind<?>, TransferTunnelKind> TRANSFER = KINDS.register("transfer", TransferTunnelKind::new);
    public static final DeferredHolder<TunnelKind<?>, RedstoneTunnelKind> REDSTONE = KINDS.register("redstone", RedstoneTunnelKind::new);

    private ModTunnelKinds() {}

    /** Creates the kind registry itself (it lives in the API, so addons can see it), then core's kinds. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NewRegistryEvent.class, event -> event.register(TunnelKinds.REGISTRY));
        KINDS.register(modEventBus);
    }
}
