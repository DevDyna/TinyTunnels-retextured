package dev.thefern2.tinytunnels.datagen;

import java.util.EnumMap;
import java.util.Map;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;

public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, TinyTunnels.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // Machines: the plain cube, plus a port-with-letter overlay on every face that has a tunnel.
        // Overlay models are hand-written (models/block/machine_port_<face>.json).
        ModBlocks.MACHINES.values().forEach(holder -> {
            MachineBlock block = holder.get();
            Identifier base = ModelTemplates.CUBE_ALL.create(block, TextureMapping.cube(block), blockModels.modelOutput);
            MultiPartGenerator parts = MultiPartGenerator.multiPart(block).with(BlockModelGenerators.plainVariant(base));
            for (var face : MachineBlock.TUNNEL_FACES.entrySet()) {
                parts = parts.with(BlockModelGenerators.condition(face.getValue(), true),
                        BlockModelGenerators.plainVariant(TinyTunnels.id("block/machine_port_" + face.getKey().getSerializedName())));
            }
            blockModels.blockStateOutput.accept(parts);
            blockModels.registerSimpleItemModel(block, base);
        });
        blockModels.createTrivialCube(ModBlocks.ROOM_WALL.get());
        // One texture per mapped face (a letter on the port), so the mapping is visible. The inward
        // property doesn't change the look.
        Map<Direction, Identifier> tunnelModels = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            String suffix = "_" + face.getSerializedName();
            tunnelModels.put(face, ModelTemplates.CUBE_ALL.createWithSuffix(ModBlocks.TUNNEL_WALL.get(), suffix,
                    TextureMapping.cube(TextureMapping.getBlockTexture(ModBlocks.TUNNEL_WALL.get(), suffix)), blockModels.modelOutput));
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.TUNNEL_WALL.get()).with(
                PropertyDispatch.initial(TunnelWallBlock.FACE, TunnelWallBlock.INWARD)
                        .generate((face, inward) -> BlockModelGenerators.plainVariant(tunnelModels.get(face)))));

        itemModels.generateFlatItem(ModItems.SHRINKER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TUNNEL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TUNNEL_WRENCH.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
    }
}
