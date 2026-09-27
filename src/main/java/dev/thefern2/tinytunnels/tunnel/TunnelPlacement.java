package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;

/**
 * The checks shared by every tunnel item: used from inside on a room's wall (not an edge or corner),
 * mapped to the first machine face that has no tunnel of any kind.
 */
final class TunnelPlacement {
    /** Where the new tunnel goes. */
    record Target(ServerLevel rooms, RoomData data, Room room, BlockPos pos, Direction inward, Direction face) {}

    /** Turns the wall into the tunnel, records it, and returns the message for the player. */
    @FunctionalInterface
    interface Placer {
        Component place(Target target);
    }

    static InteractionResult place(UseOnContext context, Placer placer) {
        BlockPos pos = context.getClickedPos();
        if (!RoomDimension.isRoomLevel(context.getLevel()) || !context.getLevel().getBlockState(pos).is(ModBlocks.ROOM_WALL.get())) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel rooms)) return InteractionResult.SUCCESS;
        Player player = context.getPlayer();

        RoomData data = RoomData.get(rooms.getServer());
        Room room = data.byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).orElse(null);
        if (room == null || !room.geometry().isShell(pos)) return InteractionResult.PASS;
        Direction inward = room.geometry().inwardNormal(pos);
        if (inward == null) {
            if (player != null) player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.edge"), true);
            return InteractionResult.FAIL;
        }
        Direction face = room.nextFreeFace(null);
        if (face == null) {
            if (player != null) player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.all_faces_used"), true);
            return InteractionResult.FAIL;
        }

        Component message = placer.place(new Target(rooms, data, room, pos, inward, face));
        context.getItemInHand().consume(1, player);
        if (player != null) player.displayClientMessage(message, true);
        return InteractionResult.SUCCESS;
    }

    private TunnelPlacement() {}
}
