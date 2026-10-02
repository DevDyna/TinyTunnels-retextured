package dev.thefern2.tinytunnels.create;

import dev.thefern2.tinytunnels.api.CoreIds;
import dev.thefern2.tinytunnels.create.kinetic.KineticLinks;
import dev.thefern2.tinytunnels.create.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import dev.thefern2.tinytunnels.create.registry.ModItems;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** The Create addon: the kinetic tunnel and its port block, built only on Tiny Tunnels' public API. */
@Mod(TinyTunnelsCreate.MODID)
public class TinyTunnelsCreate {
    public static final String MODID = "tinytunnels_create";

    public TinyTunnelsCreate(IEventBus modEventBus) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModTunnelKinds.KINDS.register(modEventBus);
        modEventBus.addListener(TinyTunnelsCreate::addToTabs);
        KineticLinks.register();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private static void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreIds.CREATIVE_TAB)) return;
        event.accept(ModItems.KINETIC_TUNNEL);
        event.accept(ModItems.KINETIC_PORT);
    }
}
