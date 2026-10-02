package dev.thefern2.tinytunnels.gametest;

import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import net.minecraft.gametest.framework.GameTestHelper;

/** The machine block's own states, now that the face looks live in the block entity (A3.8). Without Create only. */
final class MachineStateGameTests {
    /**
     * Each machine size has 2 block states (the {@code signal} property), not one per face combination. Only
     * without Create: Create's kinetic machine adds its {@code kinetic_face} property (A3.9).
     */
    static void twoStatesPerSize(GameTestHelper helper) {
        for (MachineSize size : MachineSize.values()) {
            int states = ModBlocks.MACHINES.get(size).get().getStateDefinition().getPossibleStates().size();
            helper.assertValueEqual(states, 2, "block states of the " + size.getSerializedName() + " machine");
        }
        helper.succeed();
    }

    private MachineStateGameTests() {}
}
