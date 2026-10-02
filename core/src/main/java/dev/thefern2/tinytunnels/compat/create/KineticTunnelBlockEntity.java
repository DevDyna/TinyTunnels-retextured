package dev.thefern2.tinytunnels.compat.create;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The kinetic tunnel wall's end of the link (see {@link LinkedKineticBlockEntity}). It drives the room on an IN tunnel. */
public class KineticTunnelBlockEntity extends LinkedKineticBlockEntity {
    public KineticTunnelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    protected @Nullable Link link() {
        if (!(level instanceof ServerLevel rooms)) return null;
        BlockState state = getBlockState();
        Direction face = state.getValue(KineticTunnelWallBlock.FACE);
        Room room = RoomData.get(rooms.getServer()).byChunk(new ChunkPos(worldPosition)).orElse(null);
        RoomTunnel<RedstoneMode> tunnel = room == null ? null : room.tunnel(face, ModTunnelKinds.KINETIC.get());
        if (tunnel == null || !tunnel.wall().equals(worldPosition)) return null;
        return new Link(room.id(), worldPosition, tunnel.data() == RedstoneMode.IN, KineticLinks.speedFactor(state.getValue(KineticTunnelWallBlock.INWARD), face));
    }
}
