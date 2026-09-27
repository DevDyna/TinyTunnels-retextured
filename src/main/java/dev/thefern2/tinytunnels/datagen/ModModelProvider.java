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
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.MultiPartBlockStateBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Block states plus block and item models. On 1.21.1 one provider covers both; item models go through {@link #itemModels()}. */
public class ModModelProvider extends BlockStateProvider {
    public ModModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, TinyTunnels.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // Machines: the plain cube, plus a port-with-letter overlay on every face that has a tunnel,
        // blue for tunnels and red for redstone tunnels. Overlay models are hand-written
        // (models/block/machine_port_<face>.json, machine_redstone_port_<face>[_on].json).
        ModBlocks.MACHINES.values().forEach(holder -> {
            MachineBlock block = holder.get();
            ModelFile base = cubeAll(block);
            MultiPartBlockStateBuilder parts = getMultipartBuilder(block).part().modelFile(base).addModel().end();
            for (var face : MachineBlock.PORTS.entrySet()) {
                String name = face.getKey().getSerializedName();
                parts.part().modelFile(port("block/machine_port_" + name)).addModel().condition(face.getValue(), PortKind.TUNNEL).end()
                        .part().modelFile(port("block/machine_redstone_port_" + name)).addModel().condition(face.getValue(), PortKind.REDSTONE).end()
                        .part().modelFile(port("block/machine_redstone_port_" + name + "_on")).addModel().condition(face.getValue(), PortKind.REDSTONE_ON).end();
            }
            simpleBlockItem(block, base);
        });
        simpleBlock(ModBlocks.ROOM_WALL.get());

        // One texture per mapped face (a letter on the port), so the mapping is visible. The inward
        // property doesn't change the look.
        Map<Direction, ModelFile> tunnelModels = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            String name = "tunnel_wall_" + face.getSerializedName();
            tunnelModels.put(face, models().cubeAll(name, modLoc("block/" + name)));
        }
        getVariantBuilder(ModBlocks.TUNNEL_WALL.get()).forAllStates(state ->
                ConfiguredModel.builder().modelFile(tunnelModels.get(state.getValue(TunnelWallBlock.FACE))).build());

        // Redstone tunnels: a red port with the face letter, per direction (corner marks on "out"),
        // brighter while carrying a signal.
        Map<String, ModelFile> redstoneModels = new HashMap<>();
        for (Direction face : Direction.values()) {
            for (RedstoneMode mode : RedstoneMode.values()) {
                for (boolean powered : new boolean[] {false, true}) {
                    String name = "redstone_tunnel_wall" + redstoneSuffix(face, mode, powered);
                    redstoneModels.put(name, models().cubeAll(name, modLoc("block/" + name)));
                }
            }
        }
        getVariantBuilder(ModBlocks.REDSTONE_TUNNEL_WALL.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(redstoneModels.get("redstone_tunnel_wall" + redstoneSuffix(state.getValue(RedstoneTunnelWallBlock.FACE),
                        state.getValue(RedstoneTunnelWallBlock.MODE), state.getValue(RedstoneTunnelWallBlock.POWERED))))
                .build());

        itemModels().basicItem(ModItems.SHRINKER.get());
        itemModels().basicItem(ModItems.REDSTONE_TUNNEL.get());
        itemModels().basicItem(ModItems.TUNNEL.get());
        itemModels().handheldItem(ModItems.TUNNEL_WRENCH.get());
    }

    private ModelFile port(String path) {
        return models().getExistingFile(modLoc(path));
    }

    private static String redstoneSuffix(Direction face, RedstoneMode mode, boolean powered) {
        return "_" + face.getSerializedName() + "_" + mode.getSerializedName() + (powered ? "_on" : "");
    }
}
