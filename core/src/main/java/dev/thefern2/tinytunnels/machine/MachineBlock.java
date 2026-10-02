package dev.thefern2.tinytunnels.machine;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class MachineBlock extends Block implements EntityBlock {
    public static final MapCodec<MachineBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MachineSize.CODEC.fieldOf("size").forGetter(MachineBlock::getSize),
            propertiesCodec()
    ).apply(i, MachineBlock::new));

    /**
     * True while any face has a redstone tunnel. {@link #isSignalSource} only gets the block state, so this is the
     * one thing about the faces the state still carries; everything else comes from the block entity's
     * {@link MachineFaces}, synced from {@link RoomData} by {@link MachineCore#syncFaces}.
     */
    public static final BooleanProperty SIGNAL = BooleanProperty.create("signal");

    private final MachineSize size;

    public MachineBlock(MachineSize size, Properties properties) {
        super(properties);
        this.size = size;
        registerDefaultState(stateDefinition.any().setValue(SIGNAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGNAL);
    }

    /** What the machine at {@code pos} shows on its faces (the server's last sent, or the client's last received). */
    public static MachineFaces faces(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachineHost machine ? machine.core().faces() : MachineFaces.NONE;
    }

    /** True if the machine at {@code pos} has a redstone tunnel on {@code face}. */
    public static boolean hasRedstone(BlockGetter level, BlockPos pos, Direction face) {
        return faces(level, pos).has(face, MachineFaces.REDSTONE);
    }

    /** The block state for these faces: {@code SIGNAL}. */
    protected BlockState faceState(BlockState state, MachineFaces faces) {
        return state.setValue(SIGNAL, !redstoneFaces(faces).isEmpty());
    }

    /**
     * Called by {@link MachineCore#syncFaces} after the faces changed: writes the state from {@link #faceState},
     * and when the set of redstone faces changed, does the neighbour and shape updates the old per-face state did
     * (dust connects or lets go, and the machine re-announces its redstone).
     */
    public void facesChanged(ServerLevel level, BlockPos pos, MachineFaces old, MachineFaces now) {
        BlockState state = level.getBlockState(pos);
        BlockState target = faceState(state, now);
        if (target != state) level.setBlock(pos, target, Block.UPDATE_ALL);
        if (!redstoneFaces(old).equals(redstoneFaces(now))) {
            target.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
            RedstoneTunnels.refreshLater(level, pos);
        }
    }

    private static Set<Direction> redstoneFaces(MachineFaces faces) {
        Set<Direction> redstone = EnumSet.noneOf(Direction.class);
        for (Direction face : Direction.values()) {
            if (faces.has(face, MachineFaces.REDSTONE)) redstone.add(face);
        }
        return redstone;
    }

    public MachineSize getSize() {
        return size;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(pos, state);
    }

    /** Refuses placement that would nest a room inside itself, or duplicate a machine whose room is live elsewhere. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level) {
            UUID roomId = context.getItemInHand().get(ModDataComponents.ROOM_ID.get());
            Room room = roomId == null ? null : RoomData.get(level.getServer()).room(roomId).orElse(null);
            GlobalPos here = GlobalPos.of(level.dimension(), context.getClickedPos());
            if (room != null && RoomData.get(level.getServer()).wouldNestInItself(room.id(), here, RoomDimension.key(level.getServer()))) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.translatable("message.tinytunnels.machine.inside_itself"), true);
                }
                return null;
            }
            if (room != null && MachineCore.isHostedElsewhere(level.getServer(), room, here)) {
                if (context.getPlayer() != null) {
                    GlobalPos host = room.host().orElseThrow();
                    context.getPlayer().displayClientMessage(Component.translatable("message.tinytunnels.machine.already_placed",
                            host.pos().toShortString(), host.dimension().location().toString()), true);
                }
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    // Redstone tunnels: the machine emits on OUT faces and reads IN faces on a scheduled tick.

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(SIGNAL);
    }

    /** {@code direction} points from the receiver to the machine, so the face is its opposite. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return hasRedstone(level, pos, direction.getOpposite()) ? RedstoneTunnels.machineOutput(level, pos, direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    /** {@code direction} points from the dust to the machine. */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && hasRedstone(level, pos, direction.getOpposite());
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide() && isSignalSource(state)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof MachineHost machine) RedstoneTunnels.readMachineInputs(level, machine);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean removed = !state.is(newState.getBlock());
        // Before super: that's where the block entity goes away, and its faces with it.
        MachineFaces faces = removed ? faces(level, pos) : MachineFaces.NONE;
        if (removed && level.getBlockEntity(pos) instanceof MachineHost machine) machine.core().preRemoveSideEffects(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (removed && level instanceof ServerLevel server) {
            // Blocks the machine strongly powered may still feed dust around them.
            for (Direction face : redstoneFaces(faces)) server.updateNeighborsAtExceptFromFacing(pos.relative(face), this, face.getOpposite());
        }
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof MachineHost machine) {
            machine.bindOnPlace(server, size);
        }
    }
}
