package dev.thefern2.tinytunnels.api;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A kind of tunnel: what a tunnel carries, its wall block, and its look on the machine face. Register one on
 * {@link TunnelKinds#REGISTRY_KEY} with a {@code DeferredRegister}. Core's own kinds are
 * {@code tinytunnels:transfer} (items, fluids, energy) and {@code tinytunnels:redstone}.
 *
 * <p>Core does the rest for every kind: placement checks, the room data, shell protection and repair, moving
 * a tunnel to another face with the wrench, removal, and the {@link dev.thefern2.tinytunnels.api.event.TunnelEvent}s.
 * The hooks here are called on the server.
 *
 * @param <D> the per-tunnel data, saved in the room (for example a direction, or a mode)
 */
public interface TunnelKind<D> {
    /** Saves and loads the per-tunnel data. Saved inside the room, so keep it small. */
    Codec<D> dataCodec();

    /** The data a newly placed tunnel starts with, unless the placer gives other data. */
    D defaultData();

    /** The kind's wall block: what the room wall turns into while it holds this tunnel. */
    Block wallBlock();

    /**
     * The wall's block state for a tunnel. Used on placement, by the shell repair, and when the wrench moves the
     * tunnel to another face. Must be a state of {@link #wallBlock()}; a wall already in this state is left alone,
     * so its block entity survives.
     *
     * @param face   the machine face this tunnel belongs to
     * @param inward the direction from the wall into the room
     */
    BlockState wallState(Direction face, Direction inward, D data);

    /** At most this many tunnels of this kind in one room. 6 means one per face, no extra limit. */
    default int maxPerRoom() {
        return 6;
    }

    /**
     * A reason this tunnel can't be placed here, or null to allow it. Asked after core's own checks (the room
     * dimension, a plain room wall that isn't an edge or corner, a free face, {@link #maxPerRoom()}). Nothing is
     * used up on a refusal; the reason is shown to the player.
     */
    default @Nullable Component refusePlacement(PlacementContext<D> context) {
        return null;
    }

    /** The item a removed tunnel gives back to the player (not in creative). */
    ItemStack item(D data);

    /**
     * Asked before the wrench removes a tunnel. Null to go ahead. Otherwise the message is shown and the removal
     * waits for a second click on the same wall (for example "holds 500 mB, click again to discard").
     */
    default @Nullable Component confirmRemoval(TunnelContext<D> tunnel, Player player) {
        return null;
    }

    /**
     * Called just before the tunnel wall turns back into a plain room wall: hand back contents, clear state.
     *
     * @param player who removed it, or null when it wasn't a player
     */
    default void onRemoving(TunnelContext<D> tunnel, @Nullable Player player) {}

    /**
     * An empty-hand click on the tunnel wall, for example to flip IN/OUT or cycle a mode (change the data with
     * {@link TunnelService#setData}). Return {@link InteractionResult#PASS} to do nothing. Clicks with an item
     * (buckets and the like) stay on the kind's own wall block.
     */
    default InteractionResult useWithoutItem(TunnelContext<D> tunnel, Player player) {
        return InteractionResult.PASS;
    }

    /** The overlay drawn on the machine face. The face redraws only when this changes. */
    FaceLook faceLook(D data);

    /** The kind's name, for Jade, the machine tooltip and the room command. */
    Component displayName();

    /** A one-line status for this tunnel (for example its mode), or null for none. */
    default @Nullable Component describe(D data) {
        return null;
    }
}
