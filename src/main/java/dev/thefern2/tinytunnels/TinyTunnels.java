package dev.thefern2.tinytunnels;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import dev.thefern2.tinytunnels.gametest.TinyTunnelsGameTests;
import dev.thefern2.tinytunnels.registry.ModAttachments;
import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModCreativeTabs;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.registry.ModItems;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(TinyTunnels.MODID)
public class TinyTunnels {
    public static final String MODID = "tinytunnels";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TinyTunnels(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        TinyTunnelsGameTests.register(modEventBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
