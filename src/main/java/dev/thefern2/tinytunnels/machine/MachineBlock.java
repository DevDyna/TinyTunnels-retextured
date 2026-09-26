package dev.thefern2.tinytunnels.machine;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class MachineBlock extends Block implements EntityBlock {
    public static final MapCodec<MachineBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MachineSize.CODEC.fieldOf("size").forGetter(MachineBlock::getSize),
            propertiesCodec()
    ).apply(i, MachineBlock::new));

    /**
     * One flag per face: true when that face has a tunnel. Drives the letter overlay on the outside,
     * and tells the client which faces are tunnel faces (see {@link #hasTunnel}).
     */
    public static final Map<Direction, BooleanProperty> TUNNEL_FACES = Map.of(
            Direction.DOWN, BlockStateProperties.DOWN,
            Direction.UP, BlockStateProperties.UP,
            Direction.NORTH, BlockStateProperties.NORTH,
            Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.WEST, BlockStateProperties.WEST,
            Direction.EAST, BlockStateProperties.EAST);

    private final MachineSize size;

    public MachineBlock(MachineSize size, Properties properties) {
        super(properties);
        this.size = size;
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : TUNNEL_FACES.values()) state = state.setValue(property, false);
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        TUNNEL_FACES.values().forEach(builder::add);
    }

    public static boolean hasTunnel(BlockState state, Direction face) {
        return state.getBlock() instanceof MachineBlock && state.getValue(TUNNEL_FACES.get(face));
    }

    /** The same state with the tunnel flags set to exactly {@code faces}. */
    public static BlockState withTunnelFaces(BlockState state, Set<Direction> faces) {
        for (Map.Entry<Direction, BooleanProperty> entry : TUNNEL_FACES.entrySet()) {
            state = state.setValue(entry.getValue(), faces.contains(entry.getKey()));
        }
        return state;
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
            if (room != null && RoomData.get(level.getServer()).wouldNestInItself(room.id(), here)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().sendOverlayMessage(Component.translatable("message.tinytunnels.machine.inside_itself"));
                }
                return null;
            }
            if (room != null && MachineBlockEntity.isHostedElsewhere(level.getServer(), room, here)) {
                if (context.getPlayer() != null) {
                    GlobalPos host = room.host().orElseThrow();
                    context.getPlayer().sendOverlayMessage(Component.translatable("message.tinytunnels.machine.already_placed",
                            host.pos().toShortString(), host.dimension().identifier().toString()));
                }
                return null;
            }
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.bindOnPlace(server, size);
        }
    }
}
