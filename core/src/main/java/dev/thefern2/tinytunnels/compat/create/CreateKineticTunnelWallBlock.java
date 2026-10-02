package dev.thefern2.tinytunnels.compat.create;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.TunnelWrenching;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The kinetic tunnel wall with Create: a shaft sticking into the room from the wall. Its axis is the
 * wall's, not the machine face's, which can be on any axis. Right-click with an empty hand flips the
 * direction; the wrench cycles the face and removes it, as for the other tunnels.
 */
public class CreateKineticTunnelWallBlock extends KineticTunnelWallBlock implements IRotate, EntityBlock {
    public static final MapCodec<CreateKineticTunnelWallBlock> CODEC = simpleCodec(CreateKineticTunnelWallBlock::new);

    public CreateKineticTunnelWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticTunnelBlockEntity(ModBlockEntities.KINETIC_TUNNEL.get(), pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.KINETIC_TUNNEL.get() ? (l, pos, s, entity) -> ((KineticTunnelBlockEntity) entity).tick() : null;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(INWARD).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(INWARD);
    }

    /** Only an empty hand flips the direction, so blocks can still be placed against the wall. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel rooms) TunnelWrenching.useWithoutItem(rooms, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    // The Tunnel Wrench handling (TunnelWrenching) runs first for any c:tools/wrench; this is a backstop.

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
