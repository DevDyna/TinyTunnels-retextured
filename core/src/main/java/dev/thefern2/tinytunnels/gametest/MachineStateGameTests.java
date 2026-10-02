package dev.thefern2.tinytunnels.gametest;

import java.util.UUID;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The machine block and its block entity on their own. */
final class MachineStateGameTests {
    /** Each machine size has 2 block states (the {@code signal} property), not one per face combination (A3.8). */
    static void twoStatesPerSize(GameTestHelper helper) {
        for (MachineSize size : MachineSize.values()) {
            int states = ModBlocks.MACHINES.get(size).get().getStateDefinition().getPossibleStates().size();
            helper.assertValueEqual(states, 2, "block states of the " + size.getSerializedName() + " machine");
        }
        helper.succeed();
    }

    /** A machine saved under the machine id loads as a machine with its room. */
    static void savedMachineKeepsRoom(GameTestHelper helper) {
        loadsWithRoom(helper, "machine");
    }

    private static void loadsWithRoom(GameTestHelper helper, String id) {
        UUID room = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", TinyTunnels.id(id).toString());
        tag.putUUID("room", room);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockState state = ModBlocks.MACHINES.get(MachineSize.NORMAL).get().defaultBlockState();
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof MachineHost, id + " should load as a machine, got " + loaded);
        TestRooms.assertEquals(helper, ((MachineHost) loaded).getRoomId(), room, "room id");
        helper.succeed();
    }

    private MachineStateGameTests() {}
}
