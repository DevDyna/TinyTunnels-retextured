package dev.thefern2.tinytunnels.api.event;

import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.Tunnel;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/** A tunnel changed. Posted after the room data changed; {@link #room()} already shows the change. */
public abstract class TunnelEvent extends Event {
    private final ServerLevel level;
    private final RoomView room;
    private final Tunnel<?> tunnel;

    protected TunnelEvent(ServerLevel level, RoomView room, Tunnel<?> tunnel) {
        this.level = level;
        this.room = room;
        this.tunnel = tunnel;
    }

    /** The room dimension. */
    public ServerLevel level() {
        return level;
    }

    public RoomView room() {
        return room;
    }

    /** The tunnel after the change; for {@link Removed}, as it was. */
    public Tunnel<?> tunnel() {
        return tunnel;
    }

    /** The tunnel's machine face (after the change, for {@link Moved}). */
    public Direction face() {
        return tunnel.face();
    }

    public static final class Added extends TunnelEvent {
        public Added(ServerLevel level, RoomView room, Tunnel<?> tunnel) {
            super(level, room, tunnel);
        }
    }

    /**
     * The tunnel moved to another machine face. The wall is unchanged, and so is the data, unless the kind resets
     * part of it on a move (redstone's power reading starts again at 0).
     */
    public static final class Moved extends TunnelEvent {
        private final Direction oldFace;

        public Moved(ServerLevel level, RoomView room, Tunnel<?> tunnel, Direction oldFace) {
            super(level, room, tunnel);
            this.oldFace = oldFace;
        }

        public Direction oldFace() {
            return oldFace;
        }
    }

    /** The tunnel is gone and its wall is a plain room wall again. */
    public static final class Removed extends TunnelEvent {
        public Removed(ServerLevel level, RoomView room, Tunnel<?> tunnel) {
            super(level, room, tunnel);
        }
    }

    /** The tunnel's data changed, for example a mode or redstone power. Fires often; keep listeners cheap. */
    public static final class DataChanged extends TunnelEvent {
        private final Object oldData;

        public DataChanged(ServerLevel level, RoomView room, Tunnel<?> tunnel, Object oldData) {
            super(level, room, tunnel);
            this.oldData = oldData;
        }

        /** The data before the change, of the same kind as {@link #tunnel()}'s. */
        public Object oldData() {
            return oldData;
        }
    }
}
