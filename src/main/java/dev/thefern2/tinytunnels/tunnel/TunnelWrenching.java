package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Editing tunnels with a wrench: the Tunnel Wrench, or any item tagged {@code c:tools/wrench}.
 * Right-click cycles to the next free machine face; sneak + right-click removes the tunnel and gives
 * the Tunnel item back. Handled from the click event because vanilla skips a block's own use handler
 * when sneaking with an item in hand, and so other mods' wrenches don't act on tunnels first.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class TunnelWrenching {
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Tags.Items.TOOLS_WRENCH)) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!state.is(ModBlocks.TUNNEL_WALL.get())) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel rooms) {
            Player player = event.getEntity();
            if (player.isSecondaryUseActive()) {
                remove(rooms, event.getPos(), state, player);
            } else {
                cycle(rooms, event.getPos(), state, player);
            }
        }
    }

    private static void cycle(ServerLevel rooms, BlockPos pos, BlockState state, Player player) {
        RoomData data = RoomData.get(rooms.getServer());
        Room room = mappedRoom(data, pos, state);
        if (room == null) return;
        Direction face = state.getValue(TunnelWallBlock.FACE);
        Direction next = room.nextFreeFace(face);
        if (next == null) {
            player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.no_free_face"));
            return;
        }
        ShellProtection.edit(() -> rooms.setBlock(pos, state.setValue(TunnelWallBlock.FACE, next), Block.UPDATE_ALL));
        data.removeTunnel(room.id(), face);
        data.setTunnel(room.id(), next, pos);
        CapabilityUpdates.roomChanged(rooms.getServer(), room);
        player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.mapped", TunnelWallBlock.faceName(next)));
    }

    private static void remove(ServerLevel rooms, BlockPos pos, BlockState state, Player player) {
        RoomData data = RoomData.get(rooms.getServer());
        Room room = mappedRoom(data, pos, state);
        if (room == null) return;
        ShellProtection.edit(() -> rooms.setBlock(pos, ModBlocks.ROOM_WALL.get().defaultBlockState(), Block.UPDATE_ALL));
        data.removeTunnel(room.id(), state.getValue(TunnelWallBlock.FACE));
        // Pass the room as it was, so the removed tunnel's position is notified too.
        CapabilityUpdates.roomChanged(rooms.getServer(), room);
        if (!player.getAbilities().instabuild) {
            ItemStack tunnel = new ItemStack(ModItems.TUNNEL.get());
            if (!player.getInventory().add(tunnel)) player.drop(tunnel, false);
        }
        player.sendOverlayMessage(Component.translatable("message.tinytunnels.tunnel.removed"));
    }

    /** The room this tunnel belongs to, if the room still maps its face to this position. */
    private static Room mappedRoom(RoomData data, BlockPos pos, BlockState state) {
        Room room = data.byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).orElse(null);
        return room != null && pos.equals(room.tunnels().get(state.getValue(TunnelWallBlock.FACE))) ? room : null;
    }

    private TunnelWrenching() {}
}
