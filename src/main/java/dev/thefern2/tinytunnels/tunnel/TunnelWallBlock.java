package dev.thefern2.tinytunnels.tunnel;

import com.mojang.serialization.MapCodec;

import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.wall.RoomWallBlock;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A room wall block that links to one outer face of the machine. {@link #FACE} is that face and
 * picks the texture, so the mapping is visible. Use to cycle faces; sneak-use with an empty hand
 * to take the tunnel out.
 */
public class TunnelWallBlock extends RoomWallBlock implements EntityBlock {
    public static final MapCodec<TunnelWallBlock> CODEC = simpleCodec(TunnelWallBlock::new);
    public static final EnumProperty<Direction> FACE = EnumProperty.create("face", Direction.class);

    public TunnelWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, Direction.DOWN));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TunnelBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel rooms)) return InteractionResult.SUCCESS;
        RoomData data = RoomData.get(rooms.getServer());
        Room room = data.byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).orElse(null);
        Direction face = state.getValue(FACE);
        if (room == null || !pos.equals(room.tunnels().get(face))) return InteractionResult.PASS;

        if (player.isSecondaryUseActive()) {
            ShellProtection.edit(() -> rooms.setBlock(pos, ModBlocks.ROOM_WALL.get().defaultBlockState(), Block.UPDATE_ALL));
            data.removeTunnel(room.id(), face);
            if (!player.getAbilities().instabuild) {
                ItemStack tunnel = new ItemStack(ModItems.TUNNEL.get());
                if (!player.getInventory().add(tunnel)) player.drop(tunnel, false);
            }
            CapabilityUpdates.roomChanged(rooms.getServer(), room);
            player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.removed"));
            return InteractionResult.SUCCESS;
        }

        Direction next = room.nextFreeFace(face);
        if (next == null) {
            player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.no_free_face"));
            return InteractionResult.FAIL;
        }
        ShellProtection.edit(() -> rooms.setBlock(pos, state.setValue(FACE, next), Block.UPDATE_ALL));
        data.removeTunnel(room.id(), face);
        data.setTunnel(room.id(), next, pos);
        CapabilityUpdates.roomChanged(rooms.getServer(), room);
        player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.mapped", faceName(next)));
        return InteractionResult.SUCCESS;
    }

    static Component faceName(Direction face) {
        return Component.translatable("tinytunnels.face." + face.getSerializedName());
    }
}
