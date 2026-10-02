package dev.thefern2.tinytunnels.create.datagen;

import java.util.List;
import java.util.Set;

import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/** The port drops itself; the tunnel wall has no loot (unbreakable, and the wrench gives the item back). */
public class ModBlockLootProvider extends BlockLootSubProvider {
    public ModBlockLootProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.KINETIC_PORT.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return List.of(ModBlocks.KINETIC_PORT.get());
    }
}
