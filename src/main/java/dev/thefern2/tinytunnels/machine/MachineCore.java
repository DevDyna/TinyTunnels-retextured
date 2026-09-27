package dev.thefern2.tinytunnels.machine;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.tunnel.CapabilityUpdates;
import dev.thefern2.tinytunnels.tunnel.EndpointCaches;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnels;
import dev.thefern2.tinytunnels.tunnel.TransferKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The machine logic shared by every {@link MachineHost}: which room it's bound to (the room ID
 * travels with the item when the machine is broken), binding, the inside capability caches, and the
 * load/unload/removal side effects. The owning block entity forwards its hooks here.
 */
public final class MachineCore {
    private final BlockEntity owner;
    private final MachineHost host;
    private @Nullable UUID roomId;
    private final EndpointCaches inside;

    private record InsideKey(TransferKind<?> kind, Direction face) {}

    public <B extends BlockEntity & MachineHost> MachineCore(B owner) {
        this.owner = owner;
        this.host = owner;
        this.inside = new EndpointCaches(() -> !owner.isRemoved(), this::onInsideChanged);
    }

    public @Nullable UUID getRoomId() {
        return roomId;
    }

    public Optional<Room> getRoom() {
        if (roomId == null || !(owner.getLevel() instanceof ServerLevel server)) return Optional.empty();
        return RoomData.get(server.getServer()).room(roomId);
    }

    public Optional<Room> hostedRoom() {
        if (!(owner.getLevel() instanceof ServerLevel server)) return Optional.empty();
        GlobalPos here = GlobalPos.of(server.dimension(), owner.getBlockPos());
        return getRoom().filter(room -> room.host().map(here::equals).orElse(false));
    }

    /**
     * Called once the machine is placed: binds to the room from the item, or creates a new room.
     * Placement of a machine whose room is live elsewhere is already refused in {@link MachineBlock}.
     */
    public void bindOnPlace(ServerLevel level, MachineSize size) {
        MinecraftServer server = level.getServer();
        RoomData data = RoomData.get(server);
        Room room = roomId == null ? null : data.room(roomId).orElse(null);
        if (room == null) {
            ServerLevel rooms = RoomDimension.getRoomLevel(server);
            if (rooms == null) return;
            room = data.allocate(size.getInterior());
            RoomBuilder.buildNew(rooms, room.geometry());
            roomId = room.id();
            owner.setChanged();
        }
        data.setHost(room.id(), GlobalPos.of(level.dimension(), owner.getBlockPos()));
        RoomTickets.requestUpdate();
        data.room(room.id()).ifPresent(bound -> CapabilityUpdates.roomChanged(server, bound));
        RedstoneTunnels.refreshLater(level, owner.getBlockPos());
    }

    /**
     * What a pipe outside touching machine face {@code face} sees: the capability of the block
     * inside the room next to that face's tunnel. Null when the face has no tunnel; empty when the
     * room isn't loaded or that block exposes nothing.
     */
    public <T> @Nullable T insideCapability(TransferKind<T> kind, Direction face) {
        if (!(owner.getLevel() instanceof ServerLevel server)) return null;
        Room room = getRoom().orElse(null);
        if (room == null || !room.host().map(GlobalPos.of(server.dimension(), owner.getBlockPos())::equals).orElse(false)) return null;
        BlockPos tunnel = room.tunnels().get(face);
        if (tunnel == null) return null;
        ServerLevel rooms = RoomDimension.getRoomLevel(server.getServer());
        Direction inward = room.geometry().inwardNormal(tunnel);
        if (rooms == null || inward == null) return kind.empty();
        return inside.get(new InsideKey(kind, face), kind, rooms, tunnel.relative(inward), inward.getOpposite());
    }

    private void onInsideChanged() {
        if (owner.getLevel() instanceof ServerLevel server) CapabilityUpdates.schedule(server, owner.getBlockPos());
    }

    // Follow the host: RoomTickets keeps the room loaded while this machine's chunk is ticking.

    public void onLoad() {
        debug("onLoad");
        if (owner.getLevel() instanceof ServerLevel server) {
            RoomTickets.track(host);
            // Nudge both ends: pipes may have cached "nothing" before this machine or its room loaded.
            CapabilityUpdates.schedule(server, owner.getBlockPos());
            getRoom().ifPresent(room -> CapabilityUpdates.roomChanged(server.getServer(), room));
            // Redstone tunnels may have changed while this machine was unloaded.
            RedstoneTunnels.refreshLater(server, owner.getBlockPos());
        }
    }

    public void onChunkUnloaded() {
        debug("onChunkUnloaded");
        RoomTickets.untrack(host);
    }

    public void setRemoved() {
        debug("setRemoved");
        RoomTickets.untrack(host);
        inside.clear();
    }

    // TODO(debug): remove once follow-the-host loading is verified in-game.
    private void debug(String event) {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide()) return;
        TinyTunnels.LOGGER.info("[TT-DEBUG] machine {} {} in {} room={}", event, owner.getBlockPos().toShortString(),
                level.dimension().location(), roomId == null ? "none" : roomId.toString().substring(0, 8));
    }

    /** True if another loaded (or not yet loaded) machine is the live host of this room. */
    static boolean isHostedElsewhere(MinecraftServer server, Room room, GlobalPos here) {
        if (room.host().isEmpty() || room.host().get().equals(here)) return false;
        GlobalPos host = room.host().get();
        ServerLevel hostLevel = server.getLevel(host.dimension());
        if (hostLevel == null) return false;
        // Can't check an unloaded machine without loading it; assume it's still there.
        if (!hostLevel.isLoaded(host.pos())) return true;
        return hostLevel.getBlockEntity(host.pos()) instanceof MachineHost other && room.id().equals(other.getRoomId());
    }

    /** Called from {@link MachineBlock#onRemove} while the block entity is still there. */
    public void preRemoveSideEffects(BlockPos pos) {
        if (roomId != null && owner.getLevel() instanceof ServerLevel server) {
            RoomData data = RoomData.get(server.getServer());
            // The inside stops seeing the redstone outside this machine.
            hostedRoom().ifPresent(room -> RedstoneTunnels.machineRemoved(server.getServer(), room));
            data.clearHostIf(roomId, GlobalPos.of(server.dimension(), pos));
            // Pipes inside the room now see an empty machine side.
            data.room(roomId).ifPresent(room -> CapabilityUpdates.roomChanged(server.getServer(), room));
        }
    }

    public void load(CompoundTag tag) {
        roomId = tag.hasUUID("room") ? tag.getUUID("room") : null;
    }

    public void save(CompoundTag tag) {
        if (roomId != null) tag.putUUID("room", roomId);
    }

    /** The room ID from the machine item, if it carries one. */
    public void applyImplicitRoom(@Nullable UUID fromItem) {
        if (fromItem != null) roomId = fromItem;
    }

    public void collectImplicitComponents(DataComponentMap.Builder components) {
        if (roomId != null) components.set(ModDataComponents.ROOM_ID.get(), roomId);
    }
}
