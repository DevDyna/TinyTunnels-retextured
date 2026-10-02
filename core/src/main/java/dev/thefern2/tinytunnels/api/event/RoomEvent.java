package dev.thefern2.tinytunnels.api.event;

import dev.thefern2.tinytunnels.api.RoomView;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.Event;

/**
 * A room started or stopped ticking. A room ticks exactly while its machine ticks: core adds the room chunk's
 * ticket when the machine starts ticking, and drops it when the machine stops.
 */
public abstract class RoomEvent extends Event {
    private final ServerLevel level;
    private final RoomView room;

    protected RoomEvent(ServerLevel level, RoomView room) {
        this.level = level;
        this.room = room;
    }

    /** The room dimension. */
    public ServerLevel level() {
        return level;
    }

    public RoomView room() {
        return room;
    }

    public static final class StartedTicking extends RoomEvent {
        public StartedTicking(ServerLevel level, RoomView room) {
            super(level, room);
        }
    }

    public static final class StoppedTicking extends RoomEvent {
        public StoppedTicking(ServerLevel level, RoomView room) {
            super(level, room);
        }
    }
}
