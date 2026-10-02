package dev.thefern2.tinytunnels.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Everything an addon asks core: find machines and rooms, change tunnels, and reach the block on the other side
 * of a tunnel. Get it with {@link #get()}. Unless a method says otherwise, call it on the server thread.
 *
 * <p>Every change goes through here, so core keeps the room data, shell protection, the machine face looks and
 * the capability refresh right, whichever mod made the change.
 */
public interface TunnelService {
    /**
     * Core's implementation.
     *
     * @throws IllegalStateException if Tiny Tunnels isn't loaded
     */
    static TunnelService get() {
        return ServiceHolder.get();
    }

    // Reading rooms and machines

    /** The room dimension, or null if it isn't loaded. */
    @Nullable ServerLevel roomLevel(MinecraftServer server);

    /** The machine at {@code pos}, if there is one. Works on either side; the room is server only. */
    Optional<MachineView> machineAt(Level level, BlockPos pos);

    Optional<RoomView> room(MinecraftServer server, UUID id);

    /** The room whose space (shell or interior) contains {@code pos}, in the room dimension. */
    Optional<RoomView> roomAt(ServerLevel rooms, BlockPos pos);

    /** Core's machine blocks, every size, for registering a capability on them. Kinds' wall blocks aren't in it. */
    List<Block> machineBlocks();

    // Changing tunnels

    /**
     * Places a tunnel of {@code kind} at a room wall, on the room's first free face. Runs core's checks and then
     * {@link TunnelKind#refusePlacement}, builds the wall, updates the room and posts
     * {@link dev.thefern2.tinytunnels.api.event.TunnelEvent.Added}. Doesn't take an item from the player.
     *
     * @param rooms  the room dimension
     * @param player who is placing it, for messages and the kind's checks; null when it isn't a player
     */
    <D> PlaceResult place(ServerLevel rooms, BlockPos wall, TunnelKind<D> kind, D data, @Nullable Player player);

    /**
     * Moves the tunnel on {@code face} to {@code newFace}, keeping its wall and data. Posts
     * {@link dev.thefern2.tinytunnels.api.event.TunnelEvent.Moved}.
     *
     * @return false, changing nothing, if there's no tunnel on {@code face} or {@code newFace} is used
     */
    boolean move(RoomView room, Direction face, Direction newFace);

    /**
     * Removes the tunnel on {@code face}: calls {@link TunnelKind#onRemoving}, turns the wall back into a room
     * wall, and posts {@link dev.thefern2.tinytunnels.api.event.TunnelEvent.Removed}. Doesn't give the item back
     * and doesn't ask {@link TunnelKind#confirmRemoval}; the wrench does both.
     *
     * @return false if there's no tunnel on {@code face}
     */
    boolean remove(RoomView room, Direction face, @Nullable Player player);

    /**
     * Changes the data of the tunnel on {@code face}, and rebuilds its wall state if
     * {@link TunnelKind#wallState} changed. Posts {@link dev.thefern2.tinytunnels.api.event.TunnelEvent.DataChanged}.
     *
     * @return false if there's no tunnel of {@code kind} on {@code face}
     */
    <D> boolean setData(RoomView room, Direction face, TunnelKind<D> kind, D data);

    /**
     * Call from your wall block's {@code onRemove} when the block is replaced by something else. Core puts a tunnel
     * wall back if it was removed without going through this service (a command, another mod). Does nothing if the
     * removal was core's own (a tunnel removal or a shell edit), or {@code pos} isn't a tunnel wall.
     *
     * @param level    the room dimension
     * @param oldState the wall's state before the removal
     */
    void wallRemoved(ServerLevel level, BlockPos pos, BlockState oldState);

    // The other side, for capability addons

    /**
     * The block on the other side of a tunnel. For a machine face ({@code pos} is the machine, {@code side} its
     * face): the block inside the room next to that face's tunnel wall. For a tunnel wall: the block outside, next
     * to its machine face. Empty if there's no tunnel, or the other side isn't loaded; never loads chunks.
     */
    Optional<Endpoint> otherSide(Level level, BlockPos pos, Direction side);

    /**
     * Runs a lookup that may pass through more tunnels (machines nested in rooms) under core's loop and depth
     * guard. Returns null instead of running {@code lookup} when the guard trips.
     */
    <T> @Nullable T guarded(Supplier<@Nullable T> lookup);
}
