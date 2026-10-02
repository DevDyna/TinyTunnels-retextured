package dev.thefern2.tinytunnels.create.registry;

import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.create.port.KineticPortBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TinyTunnelsCreate.MODID);

    /** The kinetic tunnel's wall, inside the room. Unbreakable like core's room walls (core cancels breaking it). */
    public static final DeferredBlock<KineticTunnelWallBlock> KINETIC_TUNNEL_WALL = BLOCKS.registerBlock("kinetic_tunnel_wall",
            KineticTunnelWallBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                    .sound(SoundType.METAL)
                    .strength(-1f, 3_600_000f)
                    .noLootTable()
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .lightLevel(state -> 12)
                    .isRedstoneConductor(ModBlocks::never));

    /** The kinetic tunnel's outside end: a flange against a machine face. */
    public static final DeferredBlock<KineticPortBlock> KINETIC_PORT = BLOCKS.registerBlock("kinetic_port",
            KineticPortBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW)
                    .sound(SoundType.METAL)
                    .strength(1.5f, 6f)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .isRedstoneConductor(ModBlocks::never));

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private ModBlocks() {}
}
