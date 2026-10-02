package dev.thefern2.tinytunnels.gametest;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import dev.thefern2.tinytunnels.TinyTunnels;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * Registers the tunnel and redstone GameTests, only when GameTests are enabled (dev runs and the GameTest
 * server, never in a production install). Run them all with {@code ./gradlew runGameTestServer},
 * or in a dev client with {@code /test runall}.
 */
public final class TinyTunnelsGameTests {
    record Test(Consumer<GameTestHelper> function, int maxTicks) {}

    private static final Map<String, Test> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("face_matrix", new Test(TunnelGameTests::faceMatrix, 100));
        TESTS.put("hopper_outside_in", new Test(TunnelGameTests::hopperOutsideIn, 200));
        TESTS.put("hopper_inside_out", new Test(TunnelGameTests::hopperInsideOut, 300));
        TESTS.put("fluid_into_cauldron", new Test(TunnelGameTests::fluidIntoCauldron, 100));
        TESTS.put("fluid_out_of_cauldron", new Test(TunnelGameTests::fluidOutOfCauldron, 100));
        TESTS.put("fluid_partial_refused", new Test(TunnelGameTests::fluidPartialRefused, 100));
        TESTS.put("empty_versus_null", new Test(TunnelGameTests::emptyVersusNull, 100));
        TESTS.put("pipe_before_tunnel", new Test(TunnelGameTests::pipeBeforeTunnel, 100));
        TESTS.put("hot_swap", new Test(TunnelGameTests::hotSwap, 100));
        TESTS.put("simulate_no_side_effects", new Test(TunnelGameTests::simulateHasNoSideEffects, 100));
        TESTS.put("machine_missing", new Test(TunnelGameTests::machineMissing, 100));
        TESTS.put("nested_chain", new Test(TunnelGameTests::nestedChain, 100));
        TESTS.put("depth_limit", new Test(TunnelGameTests::depthLimit, 100));
        TESTS.put("shell_repair", new Test(TunnelGameTests::shellRepair, 100));
        TESTS.put("follow_the_host", new Test(TunnelGameTests::followTheHost, 200));
        TESTS.put("face_flags", new Test(TunnelGameTests::faceFlags, 100));
        TESTS.put("no_nesting_in_itself", new Test(TunnelGameTests::noNestingInItself, 100));
        TESTS.put("redstone_in_face_matrix", new Test(RedstoneGameTests::inFaceMatrix, 200));
        TESTS.put("redstone_out_face_matrix", new Test(RedstoneGameTests::outFaceMatrix, 200));
        TESTS.put("redstone_analog", new Test(RedstoneGameTests::analog, 200));
        TESTS.put("redstone_toggle_mode", new Test(RedstoneGameTests::toggleMode, 300));
        TESTS.put("redstone_loop", new Test(RedstoneGameTests::loop, 300));
        TESTS.put("redstone_no_capabilities", new Test(RedstoneGameTests::noCapabilities, 100));
        TESTS.put("redstone_machine_removed", new Test(RedstoneGameTests::machineRemoved, 300));
        TESTS.put("redstone_wrench", new Test(RedstoneGameTests::wrench, 300));
        TESTS.put("redstone_nested", new Test(RedstoneGameTests::nested, 300));
        TESTS.put("redstone_item_and_ports", new Test(RedstoneGameTests::itemAndPorts, 100));
        TESTS.put("redstone_lit_look", new Test(RedstoneGameTests::litLook, 200));
        TESTS.put("redstone_shell_repair", new Test(RedstoneGameTests::shellRepairKeepsRedstone, 100));
        TESTS.put("entry_saved_exit_used", new Test(EntryGameTests::savedExitIsUsed, 100));
        TESTS.put("entry_blocked_saved_exit", new Test(EntryGameTests::blockedSavedExitFallsBack, 100));
        TESTS.put("entry_blocked_centre", new Test(EntryGameTests::blockedCentreFindsClearSpot, 100));
        TESTS.put("entry_exit_outside_room", new Test(EntryGameTests::exitOutsideRoomIgnored, 100));
        TESTS.put("bed_refused_in_room", new Test(EntryGameTests::bedRefusedInRoom, 100));
        TESTS.put("buffered_items_in", new Test(BufferedTunnelGameTests::itemsIn, 200));
        TESTS.put("buffered_wall_pull", new Test(BufferedTunnelGameTests::wallCanBePulledFrom, 100));
        TESTS.put("buffered_fluid_in", new Test(BufferedTunnelGameTests::fluidIn, 100));
        TESTS.put("buffered_active_push", new Test(BufferedTunnelGameTests::activePush, 100));
        TESTS.put("buffered_full", new Test(BufferedTunnelGameTests::full, 100));
        TESTS.put("buffered_one_way", new Test(BufferedTunnelGameTests::oneWay, 100));
        TESTS.put("buffered_mode_cycle", new Test(BufferedTunnelGameTests::modeCycle, 100));
        TESTS.put("buffered_removal", new Test(BufferedTunnelGameTests::removal, 100));
        TESTS.put("buffered_energy_passthrough", new Test(BufferedTunnelGameTests::energyPassesThrough, 100));
        TESTS.put("buffered_simulate", new Test(BufferedTunnelGameTests::simulateChangesNothing, 100));
        TESTS.put("buffered_repair", new Test(BufferedTunnelGameTests::repairKeepsBuffer, 100));
        TESTS.put("buffered_bucket", new Test(BufferedTunnelGameTests::bucket, 100));
        TESTS.put("machine_id_keeps_room", new Test(MachineStateGameTests::savedMachineKeepsRoom, 100));
        // A3.7: the machine has only the signal state (2 per size).
        TESTS.put("machine_two_states", new Test(MachineStateGameTests::twoStatesPerSize, 20));
        // Machine management M2: the wrench picks a machine up with its room, in creative too.
        TESTS.put("wrench_creative_pick_up", new Test(MachineWrenchGameTests::creativePickUp, 20));
        TESTS.put("creative_break_without_wrench", new Test(MachineWrenchGameTests::creativeBreakWithoutWrench, 20));
        // Machine management M3: sneak + wrench twice deletes a room.
        TESTS.put("delete_keeps_contents", new Test(MachineWrenchGameTests::deleteKeepsContents, 100));
        TESTS.put("delete_destroys_contents", new Test(MachineWrenchGameTests::deleteDestroysContents, 100));
        // Machine management M5: right-click with the wrench turns a machine.
        TESTS.put("wrench_turn", new Test(MachineWrenchGameTests::turn, 100));
        // Room duplicator D0: sneak + right-click a machine with an empty machine copies its room.
        TESTS.put("copy_survival", new Test(RoomCopyGameTests::survival, 20));
        TESTS.put("copy_missing", new Test(RoomCopyGameTests::missing, 20));
        TESTS.put("copy_spawner_refused", new Test(RoomCopyGameTests::spawnerRefused, 20));
        TESTS.put("copy_creative", new Test(RoomCopyGameTests::creative, 20));
        TESTS.put("copy_different_size", new Test(RoomCopyGameTests::differentSize, 20));
        // A4.2: the API events (machine, room, tunnel).
        TESTS.put("api_events", new Test(EventGameTests::events, 400));
        // A5.4: unknown kinds, with the GameTest-only test kind.
        TESTS.put("unknown_kind_kept", new Test(UnknownKindGameTests::kept, 100));
        TESTS.put("unknown_kind_returns", new Test(UnknownKindGameTests::returns, 100));
        TESTS.put("unknown_kind_placement", new Test(UnknownKindGameTests::placementRefused, 100));
        // A6.5: a world saved with the Create addon's kinetic tunnel, opened with only core.
        TESTS.put("unknown_kind_create_entry", new Test(UnknownKindGameTests::createEntry, 100));
        // A6.9: a room chunk loading with walls as air repairs them.
        TESTS.put("unknown_kind_chunk_load_repair", new Test(UnknownKindGameTests::chunkLoadRepairs, 100));
        // QA.1 (A2.5): TunnelService.get() finds core's implementation.
        TESTS.put("api_service_found", new Test(ApiGameTests::serviceFound, 20));
    }

    public static void register(IEventBus modEventBus) {
        if (!GameTestHooks.isGametestEnabled()) return;
        modEventBus.addListener((RegisterGameTestsEvent event) -> event.register(TinyTunnelsGameTests.class));
        EventGameTests.listen();
        TestTunnelKind.register(modEventBus);
    }

    /** One test per entry, named {@code tinytunnels.<name>}, all in the empty 5x5x5 structure. */
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        String structure = TinyTunnels.id("empty_5x5x5").toString();
        // -PonlyTest=<name> (system property tinytunnels.onlyTest): just that test, for repeating a flaky one.
        String only = System.getProperty("tinytunnels.onlyTest");
        return TESTS.entrySet().stream()
                .filter(entry -> only == null || only.isBlank() || entry.getKey().equals(only))
                .map(entry -> new TestFunction(TinyTunnels.MODID, TinyTunnels.MODID + "." + entry.getKey(), structure,
                        entry.getValue().maxTicks(), 0, true, entry.getValue().function()))
                .toList();
    }

    private TinyTunnelsGameTests() {}
}
