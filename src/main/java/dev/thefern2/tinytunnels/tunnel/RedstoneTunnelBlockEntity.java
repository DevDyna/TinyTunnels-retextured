package dev.thefern2.tinytunnels.tunnel;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stores nothing; exists for the load hook. The signal may have changed while the room was
 * unloaded, so the wall catches up (look, neighbours, input) at the end of the tick it loads in.
 */
public class RedstoneTunnelBlockEntity extends BlockEntity {
    public RedstoneTunnelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REDSTONE_TUNNEL.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel rooms) RedstoneTunnels.refreshLater(rooms, worldPosition);
    }
}
