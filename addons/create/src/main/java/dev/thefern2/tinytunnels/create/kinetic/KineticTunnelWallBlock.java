package dev.thefern2.tinytunnels.create.kinetic;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;

import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelContext;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.create.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The kinetic tunnel's wall, in the room's shell: a shaft sticking into the room. Its axis is the wall's, not the
 * machine face's, which can be on any axis. Right-click with an empty hand flips the direction; core's wrench
 * handling moves and removes it, as for every kind. {@link #MODE} only picks the look; the room holds the direction.
 */
public class KineticTunnelWallBlock extends Block implements IRotate, EntityBlock {
    public static final MapCodec<KineticTunnelWallBlock> CODEC = simpleCodec(KineticTunnelWallBlock::new);
    /** The machine face this tunnel belongs to. */
    public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);
    /** From the wall into the room: where the shaft points. */
    public static final EnumProperty<Direction> INWARD = EnumProperty.create("inward", Direction.class);
    public static final EnumProperty<KineticMode> MODE = EnumProperty.create("mode", KineticMode.class);

    public KineticTunnelWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN).setValue(INWARD, Direction.UP).setValue(MODE, KineticMode.OUT));
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
        return new KineticTunnelBlockEntity(pos, state);
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

    /** The kind's {@code useWithoutItem}, if this wall is still a kinetic tunnel of its room. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel rooms) {
            RoomView room = TunnelService.get().roomAt(rooms, pos).orElse(null);
            Tunnel<KineticMode> tunnel = room == null ? null
                    : room.tunnelAt(pos).flatMap(found -> found.as(ModTunnelKinds.KINETIC.get())).orElse(null);
            if (tunnel != null) ModTunnelKinds.KINETIC.get().useWithoutItem(new TunnelContext<>(rooms, room, tunnel), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Removed by a command or another mod (not core): core puts the wall back, as for its own walls. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel rooms) TunnelService.get().wallRemoved(rooms, pos, state);
    }

    // Core handles every c:tools/wrench click on tunnel walls first (Create's wrench included); this is a backstop.

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
