package dev.thefern2.tinytunnels.compat.create;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.kinetics.base.IRotate;

import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.machine.MachineFaces;
import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The machine block when Create is loaded, under the same ids. Create only turns blocks that implement
 * {@link IRotate}, so the block itself has to, and {@code getRotationAxis} only gets the state: so the shaft face is
 * in the state, as {@link #KINETIC_FACE} (Create only; the plain machine has just {@code signal}). Dropped in A6.
 */
public class KineticMachineBlock extends MachineBlock implements IRotate {
    public static final MapCodec<KineticMachineBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MachineSize.CODEC.fieldOf("size").forGetter(MachineBlock::getSize),
            propertiesCodec()
    ).apply(i, KineticMachineBlock::new));

    /** The face with the kinetic tunnel, where the shaft is; set from the machine's faces. */
    public static final EnumProperty<KineticFace> KINETIC_FACE = EnumProperty.create("kinetic_face", KineticFace.class);

    public KineticMachineBlock(MachineSize size, Properties properties) {
        super(size, properties);
        registerDefaultState(defaultBlockState().setValue(KINETIC_FACE, KineticFace.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(KINETIC_FACE);
    }

    /** The shaft face, or null. */
    public static @Nullable Direction kineticFace(BlockState state) {
        return state.hasProperty(KINETIC_FACE) ? state.getValue(KINETIC_FACE).face() : null;
    }

    private static @Nullable Direction kineticFace(MachineFaces faces) {
        for (Direction face : Direction.values()) {
            if (faces.has(face, MachineFaces.KINETIC)) return face;
        }
        return null;
    }

    @Override
    protected BlockState faceState(BlockState state, MachineFaces faces) {
        return super.faceState(state, faces).setValue(KINETIC_FACE, KineticFace.of(kineticFace(faces)));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new KineticMachineBlockEntity(ModBlockEntities.KINETIC_MACHINE.get(), pos, state);
    }

    // With Create both machine block entity ids build the kinetic machine (see ModBlockEntities).
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.KINETIC_MACHINE.get() || type == ModBlockEntities.MACHINE.get() ? (l, pos, s, entity) -> {
            if (entity instanceof KineticMachineBlockEntity machine) machine.tick();
        } : null;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        Direction face = kineticFace(state);
        return face == null ? Direction.Axis.Y : face.getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == kineticFace(state);
    }

    /**
     * A moved kinetic face moves the shaft, so the machine leaves its network before the state changes and
     * rejoins on its next tick. Create's {@code KineticBlockEntity.switchToBlockState} would do this, but
     * only for blocks that extend {@code KineticBlock}.
     */
    @Override
    public void facesChanged(ServerLevel level, BlockPos pos, MachineFaces old, MachineFaces now) {
        if (kineticFace(level.getBlockState(pos)) != kineticFace(now) && level.getBlockEntity(pos) instanceof KineticMachineBlockEntity machine) {
            machine.leaveNetwork();
        }
        super.facesChanged(level, pos, old, now);
    }

    // Create's wrench would rotate the machine, or break it on sneak. Machines are picked up the normal way.

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }
}
