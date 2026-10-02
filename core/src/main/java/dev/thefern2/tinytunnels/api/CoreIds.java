package dev.thefern2.tinytunnels.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;

/** Ids of core's own registry entries that addons may refer to. Stable: they're part of the API. */
public final class CoreIds {
    /** The plain room wall: the block a tunnel item is used on. Readable on the client too. */
    public static final ResourceKey<Block> ROOM_WALL = ResourceKey.create(Registries.BLOCK, id("room_wall"));

    /** Core's creative tab; addons may add their items to it ({@code BuildCreativeModeTabContentsEvent}). */
    public static final ResourceKey<CreativeModeTab> CREATIVE_TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("main"));

    private CoreIds() {}

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("tinytunnels", path);
    }
}
