package dev.thefern2.tinytunnels.create.kinetic;

import dev.thefern2.tinytunnels.api.CoreIds;
import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Turns a room wall into a kinetic tunnel, mapped to the next free machine face, carrying rotation out by default.
 * One per machine until A7 ({@link KineticTunnelKind#maxPerRoom()}). The outside end is a kinetic port, which the
 * player places against that machine face.
 */
public class KineticTunnelItem extends Item {
    public KineticTunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        if (!(context.getLevel() instanceof ServerLevel rooms)) {
            // Claim the click on a room wall, so the other hand doesn't also place something there.
            return context.getLevel().getBlockState(pos).is(CoreIds.ROOM_WALL) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        TunnelService service = TunnelService.get();
        RoomView room = service.roomAt(rooms, pos).orElse(null);
        if (room == null || !room.shape().isShell(pos)) return InteractionResult.PASS;

        Player player = context.getPlayer();
        PlaceResult result = service.place(rooms, pos, ModTunnelKinds.KINETIC.get(), KineticMode.OUT, player);
        if (result instanceof PlaceResult.Refused refused) {
            if (player != null) player.displayClientMessage(refused.reason(), true);
            return InteractionResult.FAIL;
        }
        context.getItemInHand().consume(1, player);
        if (player != null) player.displayClientMessage(KineticTunnelKind.modeMessage(KineticMode.OUT, ((PlaceResult.Placed) result).face()), true);
        return InteractionResult.SUCCESS;
    }
}
