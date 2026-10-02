package dev.thefern2.tinytunnels.tunnel;

import com.mojang.serialization.MapCodec;

import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.wall.RoomWallBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The kinetic tunnel wall (Create rotation) as registered without Create: an inert wall with the same
 * id and states, so removing Create never leaves a hole in a room's shell. With Create the block is
 * {@code compat/create/CreateKineticTunnelWallBlock}, which adds the shaft and the block entity.
 * {@link #MODE} only picks the look; {@link dev.thefern2.tinytunnels.room.RoomData} holds the direction.
 */
public class KineticTunnelWallBlock extends RoomWallBlock {
    public static final MapCodec<KineticTunnelWallBlock> CODEC = simpleCodec(KineticTunnelWallBlock::new);
    public static final EnumProperty<Direction> FACE = TunnelWallBlock.FACE;
    public static final EnumProperty<Direction> INWARD = TunnelWallBlock.INWARD;
    public static final EnumProperty<RedstoneMode> MODE = RedstoneTunnelWallBlock.MODE;

    public KineticTunnelWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(INWARD, Direction.UP).setValue(MODE, RedstoneMode.OUT));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, INWARD, MODE);
    }
}
