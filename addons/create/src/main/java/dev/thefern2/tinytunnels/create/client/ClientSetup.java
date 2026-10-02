package dev.thefern2.tinytunnels.create.client;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.port.KineticPortBlockEntity;
import dev.thefern2.tinytunnels.create.registry.ModBlockEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * The port's turning shaft, drawn the way Create draws an encased shaft: a full shaft along the port's axis, through
 * the static flange. {@link ShaftRenderer} draws it without Flywheel's instancing; the Flywheel visual draws it with,
 * and then the renderer is skipped. The kinetic tunnel wall needs neither: it's a plain cube with no visible shaft.
 */
@EventBusSubscriber(modid = TinyTunnelsCreate.MODID, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.KINETIC_PORT.get(), ShaftRenderer::new);
    }

    @SubscribeEvent
    static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer.<KineticPortBlockEntity>builder(ModBlockEntities.KINETIC_PORT.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .skipVanillaRender(port -> true)
                .apply());
    }

    private ClientSetup() {}
}
