package dev.thefern2.tinytunnels.machine;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.tunnel.CapabilityUpdates;
import dev.thefern2.tinytunnels.tunnel.EndpointCaches;
import dev.thefern2.tinytunnels.tunnel.TransferKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Holds which room this machine is bound to. The room ID travels with the item when the machine is broken. */
public class MachineBlockEntity extends BlockEntity {
    private @Nullable UUID roomId;
    private final EndpointCaches inside = new EndpointCaches(() -> !isRemoved(), this::onInsideChanged);

    private record InsideKey(TransferKind<?> kind, Direction face) {}

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE.get(), pos, state);
    }

    public @Nullable UUID getRoomId() {
        return roomId;
    }

    public Optional<Room> getRoom() {
        if (roomId == null || !(level instanceof ServerLevel server)) return Optional.empty();
        return RoomData.get(server.getServer()).room(roomId);
    }

    /**
     * Called once the machine is placed: binds to the room from the item, or creates a new room.
     * Placement of a machine whose room is live elsewhere is already refused in {@link MachineBlock}.
     */
    void bindOnPlace(ServerLevel level, MachineSize size) {
        MinecraftServer server = level.getServer();
        RoomData data = RoomData.get(server);
        Room room = roomId == null ? null : data.room(roomId).orElse(null);
        if (room == null) {
            ServerLevel rooms = RoomDimension.getRoomLevel(server);
            if (rooms == null) return;
            room = data.allocate(size.getInterior());
            RoomBuilder.buildNew(rooms, room.geometry());
            roomId = room.id();
            setChanged();
        }
        data.setHost(room.id(), GlobalPos.of(level.dimension(), worldPosition));
        RoomTickets.requestUpdate();
        data.room(room.id()).ifPresent(bound -> CapabilityUpdates.roomChanged(server, bound));
    }

    /**
     * What a pipe outside touching machine face {@code face} sees: the capability of the block
     * inside the room next to that face's tunnel. Null when the face has no tunnel; empty when the
     * room isn't loaded or that block exposes nothing.
     */
    public <T> @Nullable T insideCapability(TransferKind<T> kind, Direction face) {
        if (!(level instanceof ServerLevel server)) return null;
        Room room = getRoom().orElse(null);
        if (room == null || !room.host().map(GlobalPos.of(server.dimension(), worldPosition)::equals).orElse(false)) return null;
        BlockPos tunnel = room.tunnels().get(face);
        if (tunnel == null) return null;
        ServerLevel rooms = RoomDimension.getRoomLevel(server.getServer());
        Direction inward = room.geometry().inwardNormal(tunnel);
        if (rooms == null || inward == null) return kind.empty();
        return inside.get(new InsideKey(kind, face), kind, rooms, tunnel.relative(inward), inward.getOpposite());
    }

    private void onInsideChanged() {
        if (level instanceof ServerLevel server) CapabilityUpdates.schedule(server, worldPosition);
    }

    // Follow the host: RoomTickets keeps the room loaded while this machine's chunk is ticking.

    @Override
    public void onLoad() {
        super.onLoad();
        debug("onLoad");
        if (level instanceof ServerLevel server) {
            RoomTickets.track(this);
            // Nudge both ends: pipes may have cached "nothing" before this machine or its room loaded.
            CapabilityUpdates.schedule(server, worldPosition);
            getRoom().ifPresent(room -> CapabilityUpdates.roomChanged(server.getServer(), room));
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        debug("onChunkUnloaded");
        RoomTickets.untrack(this);
    }

    @Override
    public void setRemoved() {
        debug("setRemoved");
        RoomTickets.untrack(this);
        inside.clear();
        super.setRemoved();
    }

    // TODO(debug): remove once follow-the-host loading is verified in-game.
    private void debug(String event) {
        if (level == null || level.isClientSide()) return;
        TinyTunnels.LOGGER.info("[TT-DEBUG] machine {} {} in {} room={}", event, worldPosition.toShortString(),
                level.dimension().identifier(), roomId == null ? "none" : roomId.toString().substring(0, 8));
    }

    /** True if another loaded (or not yet loaded) machine is the live host of this room. */
    static boolean isHostedElsewhere(MinecraftServer server, Room room, GlobalPos here) {
        if (room.host().isEmpty() || room.host().get().equals(here)) return false;
        GlobalPos host = room.host().get();
        ServerLevel hostLevel = server.getLevel(host.dimension());
        if (hostLevel == null) return false;
        // Can't check an unloaded machine without loading it; assume it's still there.
        if (!hostLevel.isLoaded(host.pos())) return true;
        return hostLevel.getBlockEntity(host.pos()) instanceof MachineBlockEntity other && room.id().equals(other.roomId);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (roomId != null && level instanceof ServerLevel server) {
            RoomData data = RoomData.get(server.getServer());
            data.clearHostIf(roomId, GlobalPos.of(server.dimension(), pos));
            // Pipes inside the room now see an empty machine side.
            data.room(roomId).ifPresent(room -> CapabilityUpdates.roomChanged(server.getServer(), room));
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        roomId = input.read("room", UUIDUtil.CODEC).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.storeNullable("room", UUIDUtil.CODEC, roomId);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        UUID fromItem = components.get(ModDataComponents.ROOM_ID.get());
        if (fromItem != null) roomId = fromItem;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (roomId != null) components.set(ModDataComponents.ROOM_ID.get(), roomId);
    }
}
