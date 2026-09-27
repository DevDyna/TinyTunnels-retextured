package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Use on a room's wall from inside to turn it into a tunnel mapped to the first free outer face. */
public class TunnelItem extends Item {
    public TunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return TunnelPlacement.place(context, target -> {
            ShellProtection.edit(() -> target.rooms().setBlock(target.pos(), ModBlocks.TUNNEL_WALL.get().defaultBlockState()
                    .setValue(TunnelWallBlock.FACE, target.face()).setValue(TunnelWallBlock.INWARD, target.inward()), Block.UPDATE_ALL));
            target.data().setTunnel(target.room().id(), target.face(), target.pos());
            target.data().room(target.room().id()).ifPresent(updated -> CapabilityUpdates.roomChanged(target.rooms().getServer(), updated));
            return Component.translatable("message.tinytunnels.tunnel.mapped", TunnelWallBlock.faceName(target.face()));
        });
    }
}
