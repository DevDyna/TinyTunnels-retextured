package dev.thefern2.tinytunnels.machine;

import java.util.UUID;
import java.util.function.Consumer;

import dev.thefern2.tinytunnels.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

public class MachineItem extends BlockItem {
    public MachineItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        UUID roomId = stack.get(ModDataComponents.ROOM_ID.get());
        if (roomId == null) {
            builder.accept(Component.translatable("tooltip.tinytunnels.machine.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            String shortId = roomId.toString().substring(0, 8);
            builder.accept(Component.translatable("tooltip.tinytunnels.machine.bound", shortId).withStyle(ChatFormatting.GRAY));
        }
    }
}
