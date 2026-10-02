package dev.thefern2.tinytunnels.gametest;

import dev.thefern2.tinytunnels.api.TunnelService;
import net.minecraft.gametest.framework.GameTestHelper;

/** The public API as an addon sees it (QA.1). Create-free; runs with and without Create. */
final class ApiGameTests {
    /**
     * {@link TunnelService#get()} finds core's implementation through {@code META-INF/services} under FML's module
     * layer, and returns the same instance every time.
     */
    static void serviceFound(GameTestHelper helper) {
        TunnelService service;
        try {
            service = TunnelService.get();
        } catch (IllegalStateException e) {
            helper.fail("TunnelService.get() threw: " + e.getMessage());
            return;
        }
        String impl = service.getClass().getName();
        helper.assertTrue(impl.startsWith("dev.thefern2.tinytunnels.") && !impl.startsWith("dev.thefern2.tinytunnels.api."),
                "TunnelService.get() should return core's implementation, got " + impl);
        helper.assertTrue(TunnelService.get() == service, "TunnelService.get() should return the same instance every time");
        helper.succeed();
    }

    private ApiGameTests() {}
}
