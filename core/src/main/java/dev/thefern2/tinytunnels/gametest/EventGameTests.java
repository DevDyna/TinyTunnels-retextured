package dev.thefern2.tinytunnels.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.api.event.MachineEvent;
import dev.thefern2.tinytunnels.api.event.RoomEvent;
import dev.thefern2.tinytunnels.api.event.TunnelEvent;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.TunnelMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringRepresentable;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The API events, as an addon listening on {@code NeoForge.EVENT_BUS} sees them (A4.2). Create-free; runs with
 * and without Create. GameTests run side by side, so each test only looks at the events of its own room.
 */
final class EventGameTests {
    private record Recorded(UUID room, String what) {}

    private static final List<Recorded> EVENTS = new ArrayList<>();

    /** Records every API event; called once, from {@link TinyTunnelsGameTests#register}. */
    static void listen() {
        NeoForge.EVENT_BUS.addListener((MachineEvent.Placed e) -> record(e.room(), "machine placed"));
        NeoForge.EVENT_BUS.addListener((MachineEvent.Removed e) -> record(e.room(), "machine removed"));
        NeoForge.EVENT_BUS.addListener((RoomEvent.StartedTicking e) -> record(e.room(), "started ticking"));
        NeoForge.EVENT_BUS.addListener((RoomEvent.StoppedTicking e) -> record(e.room(), "stopped ticking"));
        NeoForge.EVENT_BUS.addListener((TunnelEvent.Added e) -> record(e.room(), "added " + name(e.face())));
        NeoForge.EVENT_BUS.addListener((TunnelEvent.Moved e) -> record(e.room(), "moved " + name(e.oldFace()) + " to " + name(e.face())));
        NeoForge.EVENT_BUS.addListener((TunnelEvent.DataChanged e) -> record(e.room(), "data " + name(e.face()) + " " + text(e.oldData()) + " to " + text(e.tunnel().data())));
        NeoForge.EVENT_BUS.addListener((TunnelEvent.Removed e) -> record(e.room(), "removed " + name(e.face())));
    }

    private static synchronized void record(RoomView room, String what) {
        EVENTS.add(new Recorded(room.id(), what));
    }

    private static synchronized List<String> eventsOf(UUID room) {
        return EVENTS.stream().filter(e -> e.room().equals(room)).map(Recorded::what).toList();
    }

    private static String name(Direction face) {
        return face.getSerializedName();
    }

    private static String text(Object data) {
        return data instanceof StringRepresentable named ? named.getSerializedName() : String.valueOf(data);
    }

    /**
     * Placing a machine posts {@code Placed} and {@code StartedTicking}; placing, moving, changing and removing a
     * tunnel through {@link TunnelService} posts the four tunnel events with the right faces, in order; picking the
     * machine up posts {@code Removed} and {@code StoppedTicking}.
     */
    static void events(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, TunnelGameTests.MACHINE);
        UUID id = machine.getRoomId();
        MinecraftServer server = helper.getLevel().getServer();
        TunnelService service = TunnelService.get();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    List<String> events = eventsOf(id);
                    helper.assertTrue(events.contains("machine placed"), "waiting for MachineEvent.Placed, got " + events);
                    helper.assertTrue(events.contains("started ticking"), "waiting for RoomEvent.StartedTicking, got " + events);
                })
                .thenExecute(() -> {
                    Room room = TestRooms.room(helper, machine);
                    BlockPos wall = room.geometry().wallCenter(Direction.NORTH);
                    PlaceResult placed = service.place(TestRooms.rooms(helper), wall, ModTunnelKinds.TRANSFER.get(), TunnelMode.PASSTHROUGH, null);
                    helper.assertValueEqual(placed, new PlaceResult.Placed(Direction.DOWN), "place result");
                    helper.assertTrue(service.move(view(server, id), Direction.DOWN, Direction.UP), "move");
                    helper.assertTrue(service.setData(view(server, id), Direction.UP, ModTunnelKinds.TRANSFER.get(), TunnelMode.BUFFERED_IN), "setData");
                    helper.assertTrue(service.remove(view(server, id), Direction.UP, null), "remove");
                    List<String> tunnelEvents = eventsOf(id).stream()
                            .filter(e -> !e.startsWith("machine ") && !e.endsWith(" ticking")).toList();
                    helper.assertValueEqual(tunnelEvents, List.of(
                            "added down",
                            "moved down to up",
                            "data up passthrough to buffered_in",
                            "removed up"), "tunnel events");
                })
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(TunnelGameTests.MACHINE), false))
                .thenWaitUntil(() -> {
                    List<String> events = eventsOf(id);
                    helper.assertTrue(events.contains("machine removed"), "waiting for MachineEvent.Removed, got " + events);
                    helper.assertTrue(events.contains("stopped ticking"), "waiting for RoomEvent.StoppedTicking, got " + events);
                })
                .thenSucceed();
    }

    private static RoomView view(MinecraftServer server, UUID id) {
        return TunnelService.get().room(server, id).orElseThrow();
    }

    private EventGameTests() {}
}
