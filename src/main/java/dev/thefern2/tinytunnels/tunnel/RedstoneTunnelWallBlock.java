package dev.thefern2.tinytunnels.tunnel;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.wall.RoomWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A room wall block that carries a redstone signal through one outer face of the machine, one way.
 * {@link #MODE} and {@link #POWERED} only pick the look; {@link dev.thefern2.tinytunnels.room.RoomData}
 * holds the real direction and signal. Right-click with an empty hand flips the direction, like a
 * comparator; the wrench cycles the face and removes it, as for tunnels.
 */
public class RedstoneTunnelWallBlock extends RoomWallBlock implements EntityBlock {
    public static final MapCodec<RedstoneTunnelWallBlock> CODEC = simpleCodec(RedstoneTunnelWallBlock::new);
    public static final EnumProperty<Direction> FACE = TunnelWallBlock.FACE;
    public static final EnumProperty<Direction> INWARD = TunnelWallBlock.INWARD;
    public static final EnumProperty<RedstoneMode> MODE = EnumProperty.create("mode", RedstoneMode.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public RedstoneTunnelWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(INWARD, Direction.UP)
                .setValue(MODE, RedstoneMode.IN).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, INWARD, MODE, POWERED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RedstoneTunnelBlockEntity(pos, state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return RedstoneTunnels.wallOutput(level, pos, state, direction);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return RedstoneTunnels.wallOutput(level, pos, state, direction);
    }

    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && direction.getOpposite() == state.getValue(INWARD);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && state.getValue(MODE) == RedstoneMode.OUT) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RedstoneTunnels.readWallInput(level, pos, state);
    }

    /** Only an empty hand flips the direction, so blocks can still be placed against the wall. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel rooms) RedstoneTunnels.toggleMode(rooms, pos, state, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        // The block it strongly powered may still feed dust around it.
        RedstoneTunnels.notifyEmitter(level, pos, state.getValue(INWARD), this);
    }
}
