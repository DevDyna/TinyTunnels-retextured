package dev.thefern2.tinytunnels.machine;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.event.MachineEvent;
import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.tunnel.CapabilityUpdates;
import dev.thefern2.tinytunnels.tunnel.EndpointCaches;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnels;
import dev.thefern2.tinytunnels.tunnel.TunnelBlockEntity;
import dev.thefern2.tinytunnels.tunnel.TransferKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.NeoForge;

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
    /** What each face shows: on the server the last faces sent, on the client the last received. */
    private MachineFaces faces = MachineFaces.NONE;

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
        data.room(room.id()).ifPresent(bound -> NeoForge.EVENT_BUS.post(new MachineEvent.Placed(new HostMachineView(host), bound.view())));
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
        RoomTunnel<TunnelMode> transfer = room.tunnel(face, ModTunnelKinds.TRANSFER.get());
        if (transfer == null) return null;
        BlockPos tunnel = transfer.wall();
        ServerLevel rooms = RoomDimension.getRoomLevel(server.getServer());
        // A buffered tunnel answers with its buffer; that needs the tunnel loaded, else answer empty.
        if (rooms != null && rooms.isLoaded(tunnel) && rooms.getBlockEntity(tunnel) instanceof TunnelBlockEntity tunnelEntity) {
            T bufferEnd = tunnelEntity.bufferEnd(kind, true);
            if (bufferEnd != null) return bufferEnd;
        } else if (rooms == null || !rooms.isLoaded(tunnel)) {
            return kind.empty();
        }
        Direction inward = room.geometry().inwardNormal(tunnel);
        if (rooms == null || inward == null) return kind.empty();
        return inside.get(new InsideKey(kind, face), kind, rooms, tunnel.relative(inward), inward.getOpposite());
    }

    // Face looks: derived from the hosted room on the server, sent in the update tag, drawn from ModelData.

    public MachineFaces faces() {
        return faces;
    }

    /** Server: recomputes the faces from the hosted room and, only if they changed, sends them to clients. */
    public void syncFaces() {
        if (!(owner.getLevel() instanceof ServerLevel level)) return;
        MachineFaces now = computeFaces();
        if (now.equals(faces)) return;
        MachineFaces old = faces;
        faces = now;
        if (owner.getBlockState().getBlock() instanceof MachineBlock block) block.facesChanged(level, owner.getBlockPos(), old, now);
        BlockState state = owner.getBlockState();
        level.sendBlockUpdated(owner.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
    }

    private MachineFaces computeFaces() {
        return hostedRoom().map(MachineFaces::fromRoom).orElse(MachineFaces.NONE);
    }

    /** Server: the faces for the update tag (chunk load and block entity updates). A stale machine sends none. */
    public void writeClient(CompoundTag tag) {
        // Not stored: syncFaces compares against what it last applied to the block state.
        (owner.getLevel() instanceof ServerLevel ? computeFaces() : faces).save(tag);
    }

    /** Client: new faces from the server. On a change, redraws the face looks and has pipes look again. */
    public void readClient(CompoundTag tag) {
        MachineFaces now = MachineFaces.load(tag);
        if (now.equals(faces)) return;
        faces = now;
        Level level = owner.getLevel();
        if (level == null || !level.isClientSide()) return;
        owner.requestModelDataUpdate();
        BlockState state = owner.getBlockState();
        level.sendBlockUpdated(owner.getBlockPos(), state, state, Block.UPDATE_IMMEDIATE);
        level.invalidateCapabilities(owner.getBlockPos());
    }

    /** The model data the face overlay reads. */
    public ModelData modelData() {
        return ModelData.of(MachineFaces.PROPERTY, faces);
    }

    private void onInsideChanged() {
        if (owner.getLevel() instanceof ServerLevel server) CapabilityUpdates.schedule(server, owner.getBlockPos());
    }

    // Follow the host: RoomTickets keeps the room loaded while this machine's chunk is ticking.

    public void onLoad() {
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
        RoomTickets.untrack(host);
    }

    public void setRemoved() {
        RoomTickets.untrack(host);
        inside.clear();
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
            // Only the current host: a stale or duplicated machine going away changes nothing for the room.
            hostedRoom().ifPresent(room -> NeoForge.EVENT_BUS.post(new MachineEvent.Removed(new HostMachineView(host), room.view())));
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
