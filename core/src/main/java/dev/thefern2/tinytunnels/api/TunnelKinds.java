package dev.thefern2.tinytunnels.api;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * The tunnel kind registry, {@code tinytunnels:tunnel_kind}. Register a kind with
 * {@code DeferredRegister.create(TunnelKinds.REGISTRY_KEY, "your_mod")}; look kinds up with {@link #REGISTRY}.
 *
 * <p>The registry is synced: a client without a mod that adds kinds can't join a server that has it.
 */
public final class TunnelKinds {
    public static final ResourceKey<Registry<TunnelKind<?>>> REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("tinytunnels", "tunnel_kind"));

    /** Every registered kind. Filled during registration; read it after that. */
    public static final Registry<TunnelKind<?>> REGISTRY = new RegistryBuilder<>(REGISTRY_KEY).sync(true).create();

    private TunnelKinds() {}
}
