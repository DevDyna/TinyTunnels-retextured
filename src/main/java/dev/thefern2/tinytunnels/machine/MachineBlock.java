package dev.thefern2.tinytunnels.machine;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;

public class MachineBlock extends Block implements EntityBlock {
    public static final MapCodec<MachineBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MachineSize.CODEC.fieldOf("size").forGetter(MachineBlock::getSize),
            propertiesCodec()
    ).apply(i, MachineBlock::new));

    /**
     * One property per face: what that face is linked to inside the room. Drives the letter overlay
     * on the outside, and tells the client which faces are tunnel or redstone faces (see
     * {@link #hasTunnel}, {@link #hasRedstone}). Synced from {@link RoomData}, which is authoritative.
     */
    public static final Map<Direction, EnumProperty<PortKind>> PORTS = ports();

    private static Map<Direction, EnumProperty<PortKind>> ports() {
        Map<Direction, EnumProperty<PortKind>> ports = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) ports.put(face, EnumProperty.create(face.getSerializedName(), PortKind.class));
        return Collections.unmodifiableMap(ports);
    }

    private final MachineSize size;

    public MachineBlock(MachineSize size, Properties properties) {
        super(properties);
        this.size = size;
        BlockState state = stateDefinition.any();
        for (EnumProperty<PortKind> property : PORTS.values()) state = state.setValue(property, PortKind.NONE);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        PORTS.values().forEach(builder::add);
    }

    /** True if the face has an item, fluid and energy tunnel. */
    public static boolean hasTunnel(BlockState state, Direction face) {
        return state.getBlock() instanceof MachineBlock && state.getValue(PORTS.get(face)) == PortKind.TUNNEL;
    }

    public static boolean hasRedstone(BlockState state, Direction face) {
        return state.getBlock() instanceof MachineBlock && state.getValue(PORTS.get(face)).isRedstone();
    }

    /** The same state with each face's port set from the room's tunnels. */
    public static BlockState withPorts(BlockState state, Room room) {
        for (Map.Entry<Direction, EnumProperty<PortKind>> entry : PORTS.entrySet()) {
            Direction face = entry.getKey();
            PortKind kind = room.tunnels().containsKey(face) ? PortKind.TUNNEL
                    : room.redstone().containsKey(face) ? redstonePort(room, face) : PortKind.NONE;
            state = state.setValue(entry.getValue(), kind);
        }
        return state;
    }

    /**
     * The same state with only the lit look of existing redstone ports updated from the room's
     * signals. Adding or removing ports goes through {@link #withPorts}, which also updates dust shapes.
     */
    public static BlockState withRedstoneLit(BlockState state, Room room) {
        for (Map.Entry<Direction, EnumProperty<PortKind>> entry : PORTS.entrySet()) {
            if (state.getValue(entry.getValue()).isRedstone() && room.redstone().containsKey(entry.getKey())) {
                state = state.setValue(entry.getValue(), redstonePort(room, entry.getKey()));
            }
        }
        return state;
    }

    private static PortKind redstonePort(Room room, Direction face) {
        return room.redstone().get(face).power() > 0 ? PortKind.REDSTONE_ON : PortKind.REDSTONE;
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
        for (EnumProperty<PortKind> port : PORTS.values()) {
            if (state.getValue(port).isRedstone()) return true;
        }
        return false;
    }

    /** {@code direction} points from the receiver to the machine, so the face is its opposite. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return hasRedstone(state, direction.getOpposite()) ? RedstoneTunnels.machineOutput(level, pos, direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    /** {@code direction} points from the dust to the machine. */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && hasRedstone(state, direction.getOpposite());
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
        // Before super: that's where the block entity goes away.
        if (removed && level.getBlockEntity(pos) instanceof MachineHost machine) machine.core().preRemoveSideEffects(pos);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (removed && level instanceof ServerLevel server) {
            // Blocks the machine strongly powered may still feed dust around them.
            for (Direction face : Direction.values()) {
                if (hasRedstone(state, face)) server.updateNeighborsAtExceptFromFacing(pos.relative(face), this, face.getOpposite());
            }
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
