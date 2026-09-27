package dev.thefern2.tinytunnels.datagen;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.PortKind;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
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
        // Machines: the plain cube, plus a port-with-letter overlay on every face that has a tunnel,
        // blue for tunnels and red for redstone tunnels. Overlay models are hand-written
        // (models/block/machine_port_<face>.json, machine_redstone_port_<face>[_on].json).
        ModBlocks.MACHINES.values().forEach(holder -> {
            MachineBlock block = holder.get();
            Identifier base = ModelTemplates.CUBE_ALL.create(block, TextureMapping.cube(block), blockModels.modelOutput);
            MultiPartGenerator parts = MultiPartGenerator.multiPart(block).with(BlockModelGenerators.plainVariant(base));
            for (var face : MachineBlock.PORTS.entrySet()) {
                String name = face.getKey().getSerializedName();
                parts = parts.with(BlockModelGenerators.condition(face.getValue(), PortKind.TUNNEL),
                                BlockModelGenerators.plainVariant(TinyTunnels.id("block/machine_port_" + name)))
                        .with(BlockModelGenerators.condition(face.getValue(), PortKind.REDSTONE),
                                BlockModelGenerators.plainVariant(TinyTunnels.id("block/machine_redstone_port_" + name)))
                        .with(BlockModelGenerators.condition(face.getValue(), PortKind.REDSTONE_ON),
                                BlockModelGenerators.plainVariant(TinyTunnels.id("block/machine_redstone_port_" + name + "_on")));
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

        // Redstone tunnels: a red port with the face letter, per direction (corner marks on "out"),
        // brighter while carrying a signal.
        Map<String, Identifier> redstoneModels = new HashMap<>();
        for (Direction face : Direction.values()) {
            for (RedstoneMode mode : RedstoneMode.values()) {
                for (boolean powered : new boolean[] {false, true}) {
                    String suffix = redstoneSuffix(face, mode, powered);
                    redstoneModels.put(suffix, ModelTemplates.CUBE_ALL.createWithSuffix(ModBlocks.REDSTONE_TUNNEL_WALL.get(), suffix,
                            TextureMapping.cube(TextureMapping.getBlockTexture(ModBlocks.REDSTONE_TUNNEL_WALL.get(), suffix)), blockModels.modelOutput));
                }
            }
        }
        blockModels.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.REDSTONE_TUNNEL_WALL.get()).with(
                PropertyDispatch.initial(RedstoneTunnelWallBlock.FACE, RedstoneTunnelWallBlock.INWARD, RedstoneTunnelWallBlock.MODE, RedstoneTunnelWallBlock.POWERED)
                        .generate((face, inward, mode, powered) -> BlockModelGenerators.plainVariant(redstoneModels.get(redstoneSuffix(face, mode, powered))))));

        itemModels.generateFlatItem(ModItems.SHRINKER.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.REDSTONE_TUNNEL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TUNNEL.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(ModItems.TUNNEL_WRENCH.get(), ModelTemplates.FLAT_HANDHELD_ITEM);
    }

    private static String redstoneSuffix(Direction face, RedstoneMode mode, boolean powered) {
        return "_" + face.getSerializedName() + "_" + mode.getSerializedName() + (powered ? "_on" : "");
    }
}
