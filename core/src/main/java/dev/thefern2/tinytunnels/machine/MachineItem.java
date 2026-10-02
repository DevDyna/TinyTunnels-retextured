package dev.thefern2.tinytunnels.machine;

import java.util.List;
import java.util.UUID;

import dev.thefern2.tinytunnels.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class MachineItem extends BlockItem {
    public MachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        UUID roomId = stack.get(ModDataComponents.ROOM_ID.get());
        if (roomId == null) {
            tooltip.add(Component.translatable("tooltip.tinytunnels.machine.unbound").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.tinytunnels.machine.copy_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            String shortId = roomId.toString().substring(0, 8);
            tooltip.add(Component.translatable("tooltip.tinytunnels.machine.bound", shortId).withStyle(ChatFormatting.GRAY));
        }
    }
}
