package dev.thefern2.tinytunnels.datagen;

import java.util.concurrent.CompletableFuture;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.ItemTagsProvider;

public class ModItemTagsProvider extends ItemTagsProvider {
    public ModItemTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, TinyTunnels.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        // The Tunnel Wrench is a wrench to every mod, and every wrench can edit tunnels.
        tag(Tags.Items.TOOLS_WRENCH).add(ModItems.TUNNEL_WRENCH.get());
    }
}
