package dev.thefern2.tinytunnels.machine;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import net.minecraft.core.BlockPos;
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

public class MachineBlock extends Block implements EntityBlock {
    public static final MapCodec<MachineBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            MachineSize.CODEC.fieldOf("size").forGetter(MachineBlock::getSize),
            propertiesCodec()
    ).apply(i, MachineBlock::new));

    private final MachineSize size;

    public MachineBlock(MachineSize size, Properties properties) {
        super(properties);
        this.size = size;
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
