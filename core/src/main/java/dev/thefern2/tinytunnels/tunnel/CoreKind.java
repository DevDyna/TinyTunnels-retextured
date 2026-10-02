package dev.thefern2.tinytunnels.tunnel;

import java.util.UUID;

import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;

/**
 * Core-internal extras of core's own tunnel kinds, beyond the public {@link dev.thefern2.tinytunnels.api.TunnelKind}.
 * Not API: addon kinds don't implement it, and get the defaults. {@link TunnelChanges} calls the follow-ups after
 * the room data changed and before the event is posted.
 *
 * @param <D> the kind's data
 */
public interface CoreKind<D> {
    /**
     * True if the kind's walls and the machine faces it's on answer capability lookups, so a change has pipes look
     * again ({@link CapabilityUpdates#roomChanged}). False only resyncs the machine's face looks.
     */
    default boolean hasCapabilities() {
        return true;
    }

    /** The data a tunnel keeps when the wrench moves it to another face. */
    default D movedData(D data) {
        return data;
    }

    default void added(MinecraftServer server, UUID roomId, Direction face) {}

    default void moved(MinecraftServer server, UUID roomId, Direction oldFace, Direction newFace) {}

    default void removed(MinecraftServer server, UUID roomId, Direction face) {}

    /** After {@link TunnelChanges#setData} (not the quiet {@link TunnelChanges#recordData}). */
    default void dataChanged(MinecraftServer server, UUID roomId, Direction face, D oldData, D newData) {}
}
