package dev.thefern2.tinytunnels.datagen;

import java.util.HashMap;
import java.util.Map;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Block states plus block and item models. On 1.21.1 one provider covers both; item models go through {@link #itemModels()}. */
public class ModModelProvider extends BlockStateProvider {
    public ModModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, TinyTunnels.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        // Machines: the plain cube, as one unconditioned multipart part so it fits any block state properties
        // (signal, and Create's kinetic face). The face letters are drawn by client/MachineFaceModel from the
        // block entity's synced faces: textures block/machine_port_<look>_<face>.png.
        ModBlocks.MACHINES.values().forEach(holder -> {
            MachineBlock block = holder.get();
            ModelFile base = cubeAll(block);
            getMultipartBuilder(block).part().modelFile(base).addModel().end();
            simpleBlockItem(block, base);
        });
        simpleBlock(ModBlocks.ROOM_WALL.get());

        // One texture per mapped face (a letter on the port), so the mapping is visible, with yellow
        // marks for the buffered modes: notches for in, corners for out. The inward property doesn't
        // change the look.
        Map<String, ModelFile> tunnelModels = new HashMap<>();
        for (Direction face : Direction.values()) {
            for (TunnelMode mode : TunnelMode.values()) {
                String name = "tunnel_wall" + tunnelSuffix(face, mode);
                tunnelModels.put(name, models().cubeAll(name, modLoc("block/" + name)));
            }
        }
        getVariantBuilder(ModBlocks.TUNNEL_WALL.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(tunnelModels.get("tunnel_wall" + tunnelSuffix(state.getValue(TunnelWallBlock.FACE), state.getValue(TunnelWallBlock.MODE))))
                .build());

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

        // Kinetic tunnels: a brass port with the face letter, per direction (corner marks on "out").
        Map<String, ModelFile> kineticModels = new HashMap<>();
        for (Direction face : Direction.values()) {
            for (RedstoneMode mode : RedstoneMode.values()) {
                String name = "kinetic_tunnel_wall" + redstoneSuffix(face, mode, false);
                kineticModels.put(name, models().cubeAll(name, modLoc("block/" + name)));
            }
        }
        getVariantBuilder(ModBlocks.KINETIC_TUNNEL_WALL.get()).forAllStates(state -> ConfiguredModel.builder()
                .modelFile(kineticModels.get("kinetic_tunnel_wall" + redstoneSuffix(state.getValue(KineticTunnelWallBlock.FACE),
                        state.getValue(KineticTunnelWallBlock.MODE), false)))
                .build());

        itemModels().basicItem(ModItems.SHRINKER.get());
        itemModels().basicItem(ModItems.REDSTONE_TUNNEL.get());
        itemModels().basicItem(ModItems.TUNNEL.get());
        if (ModItems.KINETIC_TUNNEL != null) itemModels().basicItem(ModItems.KINETIC_TUNNEL.get());
        itemModels().handheldItem(ModItems.TUNNEL_WRENCH.get());
    }

    private static String tunnelSuffix(Direction face, TunnelMode mode) {
        String marks = switch (mode) {
            case PASSTHROUGH -> "";
            case BUFFERED_IN -> "_in";
            case BUFFERED_OUT -> "_out";
        };
        return "_" + face.getSerializedName() + marks;
    }

    private static String redstoneSuffix(Direction face, RedstoneMode mode, boolean powered) {
        return "_" + face.getSerializedName() + "_" + mode.getSerializedName() + (powered ? "_on" : "");
    }
}
