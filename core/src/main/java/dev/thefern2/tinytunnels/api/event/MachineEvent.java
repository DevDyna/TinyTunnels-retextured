package dev.thefern2.tinytunnels.api.event;

import dev.thefern2.tinytunnels.api.MachineView;
import dev.thefern2.tinytunnels.api.RoomView;
import net.neoforged.bus.api.Event;

/** A machine was placed or is going. */
public abstract class MachineEvent extends Event {
    private final MachineView machine;
    private final RoomView room;

    protected MachineEvent(MachineView machine, RoomView room) {
        this.machine = machine;
        this.room = room;
    }

    public MachineView machine() {
        return machine;
    }

    public RoomView room() {
        return room;
    }

    /** After a placed machine is bound to its room; the room's host is this machine. */
    public static final class Placed extends MachineEvent {
        public Placed(MachineView machine, RoomView room) {
            super(machine, room);
        }
    }

    /** Before a machine goes (picked up or broken); the room still has its host. */
    public static final class Removed extends MachineEvent {
        public Removed(MachineView machine, RoomView room) {
            super(machine, room);
        }
    }
}
