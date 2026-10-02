package dev.thefern2.tinytunnels.machine;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.tunnel.TunnelChanges;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Wrench clicks on a machine block (any item tagged {@code c:tools/wrench}). Breaking a machine with a wrench picks
 * it up with its room, in creative too: there vanilla drops nothing, which would leave the room unreachable. In
 * survival the loot table already drops the bound item. A break without a wrench stays vanilla. Right-click turns
 * the machine. Sneak + right-click, twice within a few seconds, deletes the room ({@link RoomDeletion}).
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class MachineWrenching {
    private static final int CONFIRM_TICKS = 100;
    private static final Map<UUID, PendingDelete> PENDING_DELETE = new HashMap<>();

    private record PendingDelete(GlobalPos machine, long expires) {}

    @SubscribeEvent
    static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (!player.getAbilities().instabuild || !player.getMainHandItem().is(Tags.Items.TOOLS_WRENCH)) return;
        if (!(event.getState().getBlock() instanceof MachineBlock block)) return;
        BlockEntity machine = event.getLevel().getBlockEntity(event.getPos());
        if (!(machine instanceof MachineHost)) return;
        // The same item the loot table makes: the machine with the block entity's room id.
        ItemStack item = new ItemStack(block);
        item.applyComponents(machine.collectComponents());
        if (!player.getInventory().add(item)) player.drop(item, false);
    }

    // HIGHEST, like TunnelWrenching: vanilla skips a block's own use handler when sneaking with an item, and other
    // mods' wrenches shouldn't act on a machine first.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(Tags.Items.TOOLS_WRENCH)) return;
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof MachineBlock)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof ServerPlayer player) {
            if (player.isSecondaryUseActive()) {
                deleteClick(level, event.getPos(), player);
            } else {
                turn(level, event.getPos(), player);
            }
        }
    }

    /**
     * Turns the machine 90° clockwise seen from above: each side tunnel moves to the next side (N to E to S to W),
     * the top and bottom ones stay. The tunnels inside the room don't move, only the machine side each one links to.
     */
    public static void turn(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Room room = level.getBlockEntity(pos) instanceof MachineHost machine ? machine.hostedRoom().orElse(null) : null;
        ServerLevel rooms = RoomDimension.getRoomLevel(level.getServer());
        if (room == null || rooms == null) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.enter.no_room"), true);
            return;
        }
        TunnelChanges.turn(rooms, room.id(), MachineWrenching::clockwise);
        player.displayClientMessage(Component.translatable("message.tinytunnels.machine.turned"), true);
    }

    static Direction clockwise(Direction face) {
        return face.getAxis().isHorizontal() ? face.getClockWise() : face;
    }

    /** The first click asks for a second within {@link #CONFIRM_TICKS}; the second deletes the room. */
    public static void deleteClick(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof MachineHost machine) || machine.hostedRoom().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.enter.no_room"), true);
            return;
        }
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        long now = level.getGameTime();
        PendingDelete pending = PENDING_DELETE.remove(player.getUUID());
        if (pending == null || !pending.machine().equals(here) || now > pending.expires()) {
            PENDING_DELETE.put(player.getUUID(), new PendingDelete(here, now + CONFIRM_TICKS));
            player.displayClientMessage(Component.translatable(RoomDeletion.keepsContents(player)
                    ? "message.tinytunnels.machine.confirm_delete_keep" : "message.tinytunnels.machine.confirm_delete_destroy"), true);
            return;
        }
        boolean keep = RoomDeletion.keepsContents(player);
        if (RoomDeletion.delete(level, pos, player)) {
            player.displayClientMessage(Component.translatable(keep
                    ? "message.tinytunnels.machine.deleted_keep" : "message.tinytunnels.machine.deleted_destroy"), true);
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        PENDING_DELETE.clear();
    }

    private MachineWrenching() {}
}
