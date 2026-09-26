package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;

/** Use on a room's wall from inside to turn it into a tunnel mapped to the first free outer face. */
public class TunnelItem extends Item {
    public TunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        if (!RoomDimension.isRoomLevel(context.getLevel()) || !context.getLevel().getBlockState(pos).is(ModBlocks.ROOM_WALL.get())) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel rooms)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();

        RoomData data = RoomData.get(rooms.getServer());
        Room room = data.byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).orElse(null);
        if (room == null || !room.geometry().isShell(pos)) return InteractionResult.PASS;
        if (room.geometry().inwardNormal(pos) == null) {
            if (player != null) player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.edge"));
            return InteractionResult.FAIL;
        }
        Direction face = room.nextFreeFace(null);
        if (face == null) {
            if (player != null) player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.all_faces_used"));
            return InteractionResult.FAIL;
        }

        ShellProtection.edit(() -> rooms.setBlock(pos, ModBlocks.TUNNEL_WALL.get().defaultBlockState().setValue(TunnelWallBlock.FACE, face), Block.UPDATE_ALL));
        data.setTunnel(room.id(), face, pos);
        data.room(room.id()).ifPresent(updated -> CapabilityUpdates.roomChanged(rooms.getServer(), updated));
        context.getItemInHand().consume(1, player);
        if (player != null) player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.mapped", TunnelWallBlock.faceName(face)));
        return InteractionResult.SUCCESS;
    }
}
