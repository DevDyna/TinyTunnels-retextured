package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;

/**
 * What every tunnel item does on use: on a room's wall, from inside, it places a tunnel of its kind through
 * {@link TunnelChanges#place} and uses up one item. Anywhere else the click passes. Public for the kinetic tunnel
 * in {@code compat/create}.
 */
public final class TunnelPlacement {
    public static <D> InteractionResult place(UseOnContext context, TunnelKind<D> kind, D data) {
        BlockPos pos = context.getClickedPos();
        if (!RoomDimension.isRoomLevel(context.getLevel()) || !context.getLevel().getBlockState(pos).is(ModBlocks.ROOM_WALL.get())) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel rooms)) return InteractionResult.SUCCESS;
        Room room = TunnelChanges.roomAt(rooms, pos);
        if (room == null || !room.geometry().isShell(pos)) return InteractionResult.PASS;

        Player player = context.getPlayer();
        PlaceResult result = TunnelChanges.place(rooms, pos, kind, data, player);
        if (result instanceof PlaceResult.Refused refused) {
            if (player != null) player.displayClientMessage(refused.reason(), true);
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        if (player != null) player.displayClientMessage(TunnelChanges.placedMessage(kind, ((PlaceResult.Placed) result).face(), data), true);
        return InteractionResult.SUCCESS;
    }

    private TunnelPlacement() {}
}
