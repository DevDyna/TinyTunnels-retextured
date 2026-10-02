package dev.thefern2.tinytunnels.wall;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** The unbreakable wall of a room. Put back automatically if something removes it. */
public class RoomWallBlock extends Block {
    public static final MapCodec<RoomWallBlock> CODEC = simpleCodec(RoomWallBlock::new);

    public RoomWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) ShellProtection.onShellRemoved(server, pos, state);
    }
}
