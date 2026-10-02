package dev.thefern2.tinytunnels.create.gametest;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;

/**
 * The Create addon's GameTests, named {@code tinytunnels_create.<name>}. They register themselves (no call from the
 * addon's mod class), and only when GameTests are on. Run them, with core's, with
 * {@code ./gradlew :addons:create:runGameTestServer}; {@code -PonlyTest=<name>} runs just one.
 */
@EventBusSubscriber(modid = TinyTunnelsCreate.MODID)
public final class CreateGameTests {
    record Test(Consumer<GameTestHelper> function, int maxTicks) {}

    private static final Map<String, Test> TESTS = new LinkedHashMap<>();

    /** Tests left out of the default run; they run only when named with {@code -PonlyTest}. */
    private static final Set<String> ONLY_WHEN_NAMED = new HashSet<>();

    static {
        TESTS.put("kinetic_out", new Test(KineticPortGameTests::out, 100));
        TESTS.put("kinetic_sign", new Test(KineticPortGameTests::sign, 800));
        TESTS.put("kinetic_speed_change", new Test(KineticPortGameTests::speedChange, 300));
        TESTS.put("kinetic_speed_change_stepped", new Test(KineticPortGameTests::speedChangeStepped, 400));
        // Control, not registered: Create alone breaks a shaft whose motor changes speed every tick (2026-10-02).
        // TESTS.put("kinetic_stepped_control", new Test(KineticPortGameTests::steppedControl, 400));
        TESTS.put("kinetic_source_loop", new Test(KineticPortGameTests::sourceLoop, 200));
        // A6.8 reload fix: an end loaded from its save takes the link's current speed, not its saved one.
        TESTS.put("kinetic_speed_change_reload", new Test(KineticPortGameTests::speedChangeReload, 400));
        TESTS.put("kinetic_in", new Test(KineticPortGameTests::in, 100));
        TESTS.put("kinetic_toggle", new Test(KineticPortGameTests::toggle, 200));
        TESTS.put("kinetic_stress", new Test(KineticPortGameTests::stress, 100));
        TESTS.put("kinetic_shared_capacity", new Test(KineticPortGameTests::sharedCapacity, 100));
        TESTS.put("kinetic_wrench_remove", new Test(KineticPortGameTests::wrenchRemove, 100));
        TESTS.put("kinetic_wrench_cycle", new Test(KineticPortGameTests::wrenchCycle, 200));
        // Only when named (-PonlyTest=kinetic_machine_replaced): under the full suite's load its room sometimes doesn't
        // resume after the re-place, a GameTest-only effect; in game it works every time. See "kinetic_machine_replaced
        // under load" in the Notes of docs/plans/tiny-tunnels-create-addon-testing.md.
        TESTS.put("kinetic_machine_replaced", new Test(KineticPortGameTests::machineReplaced, 400));
        ONLY_WHEN_NAMED.add("kinetic_machine_replaced");
        // kinetic_one_per_machine retired in A7 (one kinetic tunnel per face now; the multi_* tests place several).
        TESTS.put("port_not_linked", new Test(KineticPortGameTests::portNotLinked, 100));
        TESTS.put("port_pops_off", new Test(KineticPortGameTests::portPopsOff, 100));
        TESTS.put("kinetic_unknown_kept", new Test(KineticUnknownGameTests::kept, 100));
        // A7.3: several kinetic tunnels per machine (need A7.1's maxPerRoom > 1).
        TESTS.put("multi_out_out", new Test(MultiKineticGameTests::outOut, 100));
        TESTS.put("multi_in_in", new Test(MultiKineticGameTests::inIn, 100));
        TESTS.put("multi_mixed", new Test(MultiKineticGameTests::mixed, 100));
        // A7.3: the loop guard (A7.2).
        TESTS.put("loop_room_motor", new Test(MultiKineticGameTests::loopRoomMotor, 300));
        TESTS.put("loop_outside_motor", new Test(MultiKineticGameTests::loopOutsideMotor, 300));
        TESTS.put("loop_diamond", new Test(MultiKineticGameTests::diamond, 200));
        TESTS.put("loop_two_rooms_one_line", new Test(MultiKineticGameTests::twoRoomsOneLine, 200));
        TESTS.put("loop_shared_load", new Test(MultiKineticGameTests::sharedLoad, 200));
        TESTS.put("loop_nested", new Test(MultiKineticGameTests::loopNested, 600)); // the biggest build; see Notes
    }

    @SubscribeEvent
    static void register(RegisterGameTestsEvent event) {
        if (GameTestHooks.isGametestEnabled()) event.register(CreateGameTests.class);
    }

    /** One test per entry, all in core's empty 5x5x5 structure. */
    @GameTestGenerator
    public static Collection<TestFunction> tests() {
        String structure = "tinytunnels:empty_5x5x5";
        String only = System.getProperty("tinytunnels.onlyTest");
        return TESTS.entrySet().stream()
                .filter(entry -> only == null || only.isBlank() ? !ONLY_WHEN_NAMED.contains(entry.getKey()) : entry.getKey().equals(only))
                .map(entry -> new TestFunction(TinyTunnelsCreate.MODID, TinyTunnelsCreate.MODID + "." + entry.getKey(), structure,
                        entry.getValue().maxTicks(), 0, true, entry.getValue().function()))
                .toList();
    }

    private CreateGameTests() {}
}
