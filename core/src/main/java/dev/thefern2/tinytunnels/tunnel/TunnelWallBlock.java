package dev.thefern2.tinytunnels.tunnel;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.wall.RoomWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * A room wall block that links to one outer face of the machine. {@link #FACE} is that face and
 * picks the texture, so the mapping is visible. The wrench cycles and removes it (see
 * {@link TunnelWrenching}); an empty hand cycles its {@link #MODE}. Any other click behaves like a
 * plain block, so blocks can be placed against it.
 */
public class TunnelWallBlock extends RoomWallBlock implements EntityBlock {
    public static final MapCodec<TunnelWallBlock> CODEC = simpleCodec(TunnelWallBlock::new);
    public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    /** The side facing into the room. Set from the room's geometry; lets the client know it too. */
    public static final EnumProperty<Direction> INWARD = EnumProperty.create("inward", Direction.class);
    /** Pass-through or buffered. The buffer itself lives in the {@link TunnelBlockEntity}. */
    public static final EnumProperty<TunnelMode> MODE = EnumProperty.create("mode", TunnelMode.class);

    public TunnelWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(INWARD, Direction.UP)
                .setValue(MODE, TunnelMode.PASSTHROUGH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, INWARD, MODE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TunnelBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.TUNNEL.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<TunnelBlockEntity>) (tickLevel, pos, tickState, tunnel) -> tunnel.serverTick();
    }

    /**
     * An empty hand changes the mode. On a buffered tunnel a bucket (or any fluid container) fills or
     * empties the buffer. Anything else is used as normal, so blocks can still be placed against the wall.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (state.getValue(MODE).isBuffered() && FluidUtil.getFluidHandler(stack).isPresent()) {
            if (level.isClientSide()) return ItemInteractionResult.SUCCESS;
            if (level.getBlockEntity(pos) instanceof TunnelBlockEntity tunnel && tunnel.useFluidContainer(player, hand)) {
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.CONSUME;
        }
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel rooms) TunnelWrenching.useWithoutItem(rooms, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    static Component faceName(Direction face) {
        return Component.translatable("tinytunnels.face." + face.getSerializedName());
    }
}
