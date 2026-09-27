package dev.thefern2.tinytunnels.teleport;

import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Use on a machine to enter its room; use anywhere inside a room to go back out. */
public class ShrinkerItem extends Item {
    private static final int COOLDOWN_TICKS = 20;

    public ShrinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof MachineBlock)) {
            return InteractionResult.PASS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(this) || RoomTeleporter.isEntering(player)) {
            return InteractionResult.FAIL;
        }

        Room room = level.getBlockEntity(context.getClickedPos()) instanceof MachineHost machine ? machine.getRoom().orElse(null) : null;
        if (room == null) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.enter.no_room"), true);
            return InteractionResult.FAIL;
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        RoomTeleporter.enter(player, room);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!RoomDimension.isRoomLevel(level)) {
            return InteractionResultHolder.pass(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            RoomTeleporter.exit(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
