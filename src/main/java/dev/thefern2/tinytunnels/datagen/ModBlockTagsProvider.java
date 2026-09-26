package dev.thefern2.tinytunnels.datagen;

import java.util.concurrent.CompletableFuture;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, TinyTunnels.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        var pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        ModBlocks.MACHINES.values().forEach(block -> pickaxe.add(block.get()));
    }
}
