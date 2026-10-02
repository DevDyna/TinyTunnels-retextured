package dev.thefern2.tinytunnels.create.datagen;

import java.util.concurrent.CompletableFuture;

import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ModBlockTagsProvider extends BlockTagsProvider {
    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, ExistingFileHelper files) {
        super(output, registries, TinyTunnelsCreate.MODID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.KINETIC_PORT.get());
    }
}
