package dev.thefern2.tinytunnels.tunnel;

import com.mojang.serialization.MapCodec;

import dev.thefern2.tinytunnels.wall.RoomWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A room wall block that links to one outer face of the machine. {@link #FACE} is that face and
 * picks the texture, so the mapping is visible. Edited only with a wrench (see {@link TunnelWrenching});
 * any other click on it behaves like a plain block.
 */
public class TunnelWallBlock extends RoomWallBlock implements EntityBlock {
    public static final MapCodec<TunnelWallBlock> CODEC = simpleCodec(TunnelWallBlock::new);
    public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    /** The side facing into the room. Set from the room's geometry; lets the client know it too. */
    public static final EnumProperty<Direction> INWARD = EnumProperty.create("inward", Direction.class);

    public TunnelWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(INWARD, Direction.UP));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, INWARD);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TunnelBlockEntity(pos, state);
    }

    static Component faceName(Direction face) {
        return Component.translatable("tinytunnels.face." + face.getSerializedName());
    }
}
