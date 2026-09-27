package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * Use on a room's wall from inside to turn it into a redstone tunnel mapped to the first free outer
 * face. It starts carrying the signal in, from outside the machine into the room.
 */
public class RedstoneTunnelItem extends Item {
    public RedstoneTunnelItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return TunnelPlacement.place(context, target -> {
            ShellProtection.edit(() -> target.rooms().setBlock(target.pos(), ModBlocks.REDSTONE_TUNNEL_WALL.get().defaultBlockState()
                    .setValue(RedstoneTunnelWallBlock.FACE, target.face()).setValue(RedstoneTunnelWallBlock.INWARD, target.inward())
                    .setValue(RedstoneTunnelWallBlock.MODE, RedstoneMode.IN), Block.UPDATE_ALL));
            target.data().setRedstoneTunnel(target.room().id(), target.face(), target.pos(), RedstoneMode.IN);
            CapabilityUpdates.syncFaces(target.room().id());
            RedstoneTunnels.tunnelChanged(target.rooms().getServer(), target.room().id(), target.face());
            return RedstoneTunnels.modeMessage(RedstoneMode.IN, target.face());
        });
    }
}
