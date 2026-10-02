package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelContext;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Clicks on tunnel walls, for every kind. With a wrench (the Tunnel Wrench, or any item tagged
 * {@code c:tools/wrench}): right-click moves the tunnel to the next free machine face; sneak + right-click removes
 * it (after the kind's {@code confirmRemoval}) and gives its item back. With an empty hand: the kind's
 * {@code useWithoutItem}. The wrench is handled from the click event because vanilla skips a block's own use handler
 * when sneaking with an item in hand, and so other mods' wrenches don't act on tunnels first.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class TunnelWrenching {
    // HIGHEST: Create's WrenchEventHandler takes any c:tools/wrench click on its rotatable blocks at HIGH and
    // cancels it, and the kinetic tunnel wall is one of them. Create skips an already cancelled event.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Tags.Items.TOOLS_WRENCH)) return;
        if (!ShellProtection.isTunnelWall(event.getLevel().getBlockState(event.getPos()))) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel rooms) {
            Room room = TunnelChanges.roomAt(rooms, event.getPos());
            Direction face = room == null ? null : room.faceAt(event.getPos());
            if (face == null) return;
            Player player = event.getEntity();
            if (player.isSecondaryUseActive()) {
                remove(rooms, room, room.view(face), player);
            } else {
                move(rooms, room, face, player);
            }
        }
    }

    private static void move(ServerLevel rooms, Room room, Direction face, Player player) {
        Direction next = room.nextFreeFace(face);
        if (next == null) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.no_free_face"), true);
            return;
        }
        TunnelChanges.move(rooms, room.id(), face, next);
        Room moved = RoomData.get(rooms.getServer()).room(room.id()).orElse(null);
        Tunnel<?> tunnel = moved == null ? null : moved.view(next);
        if (tunnel != null) player.displayClientMessage(placedMessage(tunnel), true);
    }

    private static <D> Component placedMessage(Tunnel<D> tunnel) {
        return TunnelChanges.placedMessage(tunnel.kind(), tunnel.face(), tunnel.data());
    }

    private static <D> void remove(ServerLevel rooms, Room room, Tunnel<D> tunnel, Player player) {
        TunnelKind<D> kind = tunnel.kind();
        Component confirm = kind.confirmRemoval(new TunnelContext<>(rooms, room.view(), tunnel), player);
        if (confirm != null) {
            player.displayClientMessage(confirm, true);
            return;
        }
        TunnelChanges.remove(rooms, room.id(), tunnel.face(), player);
        ItemStack item = kind.item(tunnel.data());
        if (!player.getAbilities().instabuild && !item.isEmpty() && !player.getInventory().add(item)) player.drop(item, false);
        player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.removed"), true);
    }

    /**
     * An empty-hand click on a tunnel wall: the kind's {@code useWithoutItem}, if the wall is still a mapped tunnel.
     * Called from the wall blocks, on the server.
     */
    public static InteractionResult useWithoutItem(ServerLevel rooms, BlockPos pos, Player player) {
        Room room = TunnelChanges.roomAt(rooms, pos);
        Direction face = room == null ? null : room.faceAt(pos);
        Tunnel<?> tunnel = face == null ? null : room.view(face);
        return tunnel == null ? InteractionResult.PASS : use(rooms, room, tunnel, player);
    }

    private static <D> InteractionResult use(ServerLevel rooms, Room room, Tunnel<D> tunnel, Player player) {
        return tunnel.kind().useWithoutItem(new TunnelContext<>(rooms, room.view(), tunnel), player);
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        TransferTunnelKind.clearPending();
    }

    private TunnelWrenching() {}
}
