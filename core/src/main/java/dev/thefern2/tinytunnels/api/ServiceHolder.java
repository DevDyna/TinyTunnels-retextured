package dev.thefern2.tinytunnels.api;

import java.util.ServiceLoader;

import org.jspecify.annotations.Nullable;

/** Finds core's {@link TunnelService} once. Core ships it as {@code META-INF/services/...TunnelService}. */
final class ServiceHolder {
    private static volatile @Nullable TunnelService instance;

    private ServiceHolder() {}

    static TunnelService get() {
        TunnelService service = instance;
        if (service == null) {
            service = ServiceLoader.load(TunnelService.class, TunnelService.class.getClassLoader())
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Tiny Tunnels isn't loaded"));
            instance = service;
        }
        return service;
    }
}
