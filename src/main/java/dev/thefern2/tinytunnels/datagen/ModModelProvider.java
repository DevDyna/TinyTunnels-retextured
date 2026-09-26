package dev.thefern2.tinytunnels.datagen;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.data.PackOutput;

public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, TinyTunnels.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        ModBlocks.MACHINES.values().forEach(block -> blockModels.createTrivialCube(block.get()));
        blockModels.createTrivialCube(ModBlocks.ROOM_WALL.get());
        // One texture per mapped face (a letter on the port), so the mapping is visible.
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.TUNNEL_WALL.get()).with(
                PropertyDispatch.initial(TunnelWallBlock.FACE).generate(face -> BlockModelGenerators.plainVariant(
                        ModelTemplates.CUBE_ALL.createWithSuffix(ModBlocks.TUNNEL_WALL.get(), "_" + face.getSerializedName(),
                                TextureMapping.cube(TextureMapping.getBlockTexture(ModBlocks.TUNNEL_WALL.get(), "_" + face.getSerializedName())),
                                blockModels.modelOutput)))));

        itemModels.generateFlatItem(ModItems.SHRINKER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TUNNEL.get(), ModelTemplates.FLAT_ITEM);
    }
}
