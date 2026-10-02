package dev.thefern2.tinytunnels.create.registry;

import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelKind;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The addon's tunnel kinds, on core's kind registry. */
public final class ModTunnelKinds {
    public static final DeferredRegister<TunnelKind<?>> KINDS = DeferredRegister.create(TunnelKinds.REGISTRY_KEY, TinyTunnelsCreate.MODID);

    /** {@code tinytunnels_create:kinetic}: Create rotation through a machine face. */
    public static final DeferredHolder<TunnelKind<?>, KineticTunnelKind> KINETIC = KINDS.register("kinetic", KineticTunnelKind::new);

    private ModTunnelKinds() {}
}
