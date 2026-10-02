package dev.thefern2.tinytunnels.tunnel;

import com.mojang.serialization.MapCodec;

import dev.thefern2.tinytunnels.wall.RoomWallBlock;
import net.minecraft.world.level.block.Block;

/**
 * {@code tinytunnels:unknown_tunnel_wall}: the wall of a tunnel whose kind isn't registered (its mod was removed).
 * Inert, with no block entity and no states; unbreakable and repaired like every room wall, so the shell has no
 * hole. The room keeps the tunnel's saved entry ({@link dev.thefern2.tinytunnels.room.UnknownTunnel}), and when the
 * kind is back the shell repair puts the real wall here again. The wrench can't move or remove it.
 */
public class UnknownTunnelWallBlock extends RoomWallBlock {
    public static final MapCodec<UnknownTunnelWallBlock> CODEC = simpleCodec(UnknownTunnelWallBlock::new);

    public UnknownTunnelWallBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }
}
