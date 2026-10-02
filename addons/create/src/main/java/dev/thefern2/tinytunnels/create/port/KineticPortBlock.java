package dev.thefern2.tinytunnels.create.port;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.kinetics.base.IRotate;

import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.create.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The kinetic tunnel's outside end: a thin flange against a machine face, with a turning shaft pointing out along
 * {@link #FACING}, which is that machine face. It only stays against a machine, the way a button needs a wall, and
 * pops off as an item when the machine goes. The machine block itself stays plain; see {@link KineticPortBlockEntity}
 * for how the port finds its tunnel.
 */
public class KineticPortBlock extends DirectionalBlock implements IRotate, EntityBlock {
    public static final MapCodec<KineticPortBlock> CODEC = simpleCodec(KineticPortBlock::new);

    /**
     * The plate: 2 px deep against the machine, 14 px wide. The shaft: 4 px wide, across the block (drawn turning by
     * Create's shaft renderer; see {@code client/ClientSetup}).
     */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.values()) {
            SHAPES.put(facing, Shapes.or(box(facing, 7, 0, 2), box(facing, 2, 2, 16)));
        }
    }

    private static volatile @Nullable Set<Block> machineBlocks;

    public KineticPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Facing the clicked face, so clicking a machine face puts the port flat against it. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Only against a machine block, on the side away from {@link #FACING}. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return isMachine(level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())));
    }

    /** The machine behind went: pop off (as an item, through {@code updateOrDestroy}). */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    private static boolean isMachine(BlockState state) {
        Set<Block> blocks = machineBlocks;
        if (blocks == null) {
            blocks = Set.copyOf(TunnelService.get().machineBlocks());
            machineBlocks = blocks;
        }
        return blocks.contains(state.getBlock());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** A box centred on the facing axis, {@code half} px either side, from {@code from} to {@code to} px out from the machine. */
    private static VoxelShape box(Direction facing, double half, double from, double to) {
        double lo = 8 - half;
        double hi = 8 + half;
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        double a = positive ? from : 16 - to;
        double b = positive ? to : 16 - from;
        return switch (facing.getAxis()) {
            case X -> Block.box(a, lo, lo, b, hi, hi);
            case Y -> Block.box(lo, a, lo, hi, b, hi);
            case Z -> Block.box(lo, lo, a, hi, hi, b);
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.KINETIC_PORT.get() ? (l, pos, s, entity) -> ((KineticPortBlockEntity) entity).tick() : null;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    /** Only the stub: the plate side is against the machine, which isn't a Create block. */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING);
    }

    /** Create's wrench would turn the port off the machine. Sneak-wrench still picks it up (Create's default). */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
