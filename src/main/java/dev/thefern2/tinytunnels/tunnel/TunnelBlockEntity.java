package dev.thefern2.tinytunnels.tunnel;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Proxies the outside of the machine into the room. Stores nothing: the room comes from the chunk,
 * the face from the block state, the inward side from the room's geometry, and the machine's
 * position from {@link RoomData} at query time, so a moved machine is followed automatically.
 */
public class TunnelBlockEntity extends BlockEntity {
    private final EndpointCaches outside = new EndpointCaches(() -> !isRemoved(), this::onOutsideChanged);

    public TunnelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TUNNEL.get(), pos, state);
    }

    /** The side facing into the room; the only side a tunnel exposes anything on. */
    @Nullable Direction inward() {
        Room room = room();
        return room == null ? null : room.geometry().inwardNormal(worldPosition);
    }

    /**
     * What a pipe inside the room sees: the capability of the block next to the machine's mapped
     * face. Empty when the machine isn't placed or that block isn't loaded; null when this block
     * isn't (or is no longer) a mapped tunnel.
     */
    <T> @Nullable T outsideCapability(TransferKind<T> kind) {
        if (!(level instanceof ServerLevel rooms)) return null;
        Room room = room();
        Direction face = getBlockState().getValue(TunnelWallBlock.FACE);
        if (room == null || !worldPosition.equals(room.tunnels().get(face))) return null;
        GlobalPos host = room.host().orElse(null);
        if (host == null) return kind.empty();
        ServerLevel hostLevel = rooms.getServer().getLevel(host.dimension());
        if (hostLevel == null) return kind.empty();
        return outside.get(kind, kind, hostLevel, host.pos().relative(face), face.getOpposite());
    }

    private @Nullable Room room() {
        if (!(level instanceof ServerLevel rooms)) return null;
        return RoomData.get(rooms.getServer()).byChunk(new ChunkPos(worldPosition.getX() >> 4, worldPosition.getZ() >> 4)).orElse(null);
    }

    private void onOutsideChanged() {
        if (level instanceof ServerLevel rooms) CapabilityUpdates.schedule(rooms, worldPosition);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // The room loads after the machine, so pipes outside may have cached "nothing"; nudge both ends.
        if (level instanceof ServerLevel rooms) {
            CapabilityUpdates.schedule(rooms, worldPosition);
            Room room = room();
            if (room != null) CapabilityUpdates.roomChanged(rooms.getServer(), room);
        }
    }

    @Override
    public void setRemoved() {
        outside.clear();
        super.setRemoved();
    }
}
