package dev.thefern2.tinytunnels.room;

import dev.thefern2.tinytunnels.Config;
import dev.thefern2.tinytunnels.TinyTunnels;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The 1.21.1 dimension type can only switch beds off by making them explode ({@code bed_works: false}),
 * like the Nether. Unless {@link Config#ROOM_BEDS_EXPLODE} allows that, using a bed inside a room
 * does nothing, as on 26.x. Items can still be used on the bed, so blocks can be placed against it.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class RoomBeds {
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide() || Config.ROOM_BEDS_EXPLODE.getAsBoolean() || !RoomDimension.isRoomLevel(level)) return;
        if (!level.getBlockState(event.getPos()).is(BlockTags.BEDS)) return;
        event.setUseBlock(TriState.FALSE);
        // The event fires once per hand; say it once.
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            event.getEntity().displayClientMessage(Component.translatable("message.tinytunnels.bed.no_sleep"), true);
        }
    }

    private RoomBeds() {}
}
