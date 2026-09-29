package dev.thefern2.tinytunnels.tunnel;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Links the outside of the machine into the room. In pass-through mode it stores nothing: the room
 * comes from the chunk, the face from the block state, the inward side from the room's geometry, and
 * the machine's position from {@link RoomData} at query time, so a moved machine is followed
 * automatically. In a buffered mode it also holds a {@link TunnelBuffer} and pushes it forward.
 */
public class TunnelBlockEntity extends BlockEntity {
    private final EndpointCaches outside = new EndpointCaches(() -> !isRemoved(), this::onOutsideChanged);
    /** The block inside the room against this wall; only used to push a buffered-in tunnel forward. */
    private final EndpointCaches inside = new EndpointCaches(() -> !isRemoved(), () -> {});
    private final TunnelBuffer buffer = new TunnelBuffer(this::setChanged);

    public TunnelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TUNNEL.get(), pos, state);
    }

    public TunnelMode mode() {
        return getBlockState().getValue(TunnelWallBlock.MODE);
    }

    public TunnelBuffer buffer() {
        return buffer;
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

    /**
     * One end of the buffer, if this tunnel is buffered and {@code kind} is buffered (items and fluids;
     * energy always passes through). {@code machineSide} picks the end: for buffered-in the machine face
     * fills and the wall drains, for buffered-out the other way round. Null means pass through.
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T bufferEnd(TransferKind<T> kind, boolean machineSide) {
        TunnelMode mode = mode();
        if (!mode.isBuffered()) return null;
        boolean fill = machineSide == (mode == TunnelMode.BUFFERED_IN);
        if (kind == TransferKind.ITEM) return (T) buffer.items(fill);
        if (kind == TransferKind.FLUID) return (T) buffer.fluid(fill);
        return null;
    }

    /** A buffered tunnel pushes its contents forward into whatever accepts them on the draining side. */
    void serverTick() {
        TunnelMode mode = mode();
        if (!mode.isBuffered() || buffer.isEmpty() || !(level instanceof ServerLevel rooms)) return;
        IItemHandler items = forwardTarget(rooms, mode, TransferKind.ITEM);
        IFluidHandler fluid = forwardTarget(rooms, mode, TransferKind.FLUID);
        if (!ProxyGuard.enter()) return;
        try {
            if (items != null) buffer.pushItems(items);
            if (fluid != null) buffer.pushFluid(fluid);
        } finally {
            ProxyGuard.exit();
        }
    }

    private <T> @Nullable T forwardTarget(ServerLevel rooms, TunnelMode mode, TransferKind<T> kind) {
        if (mode == TunnelMode.BUFFERED_OUT) return outsideCapability(kind);
        Direction inward = inward();
        if (inward == null) return null;
        return inside.get(kind, kind, rooms, worldPosition.relative(inward), inward.getOpposite());
    }

    /** An empty-hand click: pass-through → buffered in → buffered out → pass-through. */
    void cycleMode(Player player) {
        if (!(level instanceof ServerLevel rooms)) return;
        Room room = room();
        Direction face = getBlockState().getValue(TunnelWallBlock.FACE);
        if (room == null || !worldPosition.equals(room.tunnels().get(face))) return;
        TunnelMode next = mode().next();
        if (!next.isBuffered() && !buffer.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.not_empty"), true);
            return;
        }
        setMode(rooms, room, next);
        rooms.playSound(null, worldPosition, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, next.isBuffered() ? 0.6f : 0.5f);
        player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.mode", next.displayName()), true);
    }

    /** Changes the mode and tells pipes on both ends to look again. */
    public void setMode(ServerLevel rooms, Room room, TunnelMode mode) {
        BlockState state = getBlockState().setValue(TunnelWallBlock.MODE, mode);
        ShellProtection.edit(() -> rooms.setBlock(worldPosition, state, Block.UPDATE_ALL));
        CapabilityUpdates.roomChanged(rooms.getServer(), room);
    }

    /** For wrench removal: the stacks to hand back, emptying the buffer. */
    List<ItemStack> takeItems() {
        return buffer.takeItems();
    }

    FluidStack fluidContents() {
        return buffer.fluidContents();
    }

    /** A bucket (or any fluid container) on a buffered tunnel: fills or empties it through the buffer. */
    boolean useFluidContainer(Player player, InteractionHand hand) {
        return mode().isBuffered() && FluidUtil.interactWithFluidHandler(player, hand, buffer.tank());
    }

    void discardFluid() {
        buffer.discardFluid();
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
            // Tunnels placed before the inward property existed: correct it on the next tick.
            rooms.getServer().execute(this::fixInwardState);
        }
    }

    private void fixInwardState() {
        Direction inward = inward();
        BlockState state = getBlockState();
        if (isRemoved() || inward == null || !(level instanceof ServerLevel rooms) || state.getValue(TunnelWallBlock.INWARD) == inward) return;
        ShellProtection.edit(() -> rooms.setBlock(worldPosition, state.setValue(TunnelWallBlock.INWARD, inward), Block.UPDATE_ALL));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("buffer")) buffer.load(registries, tag.getCompound("buffer"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!buffer.isEmpty()) tag.put("buffer", buffer.save(registries));
    }

    @Override
    public void setRemoved() {
        outside.clear();
        inside.clear();
        super.setRemoved();
    }
}
