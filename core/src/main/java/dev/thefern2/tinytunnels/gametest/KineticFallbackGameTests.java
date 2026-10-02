package dev.thefern2.tinytunnels.gametest;

import java.util.UUID;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.compat.Compat;
import dev.thefern2.tinytunnels.compat.create.CreateBlocks;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kinetic tunnel data that has to work with or without Create (K11). Create-free; runs in both the
 * normal GameTest run and {@code ./gradlew runGameTestServer -PnoCreate}.
 */
final class KineticFallbackGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    /** A machine saved under the kinetic_machine id loads as a machine with its room, Create or not. */
    static void kineticIdKeepsRoom(GameTestHelper helper) {
        UUID room = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", TinyTunnels.id("kinetic_machine").toString());
        tag.putUUID("room", room);
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = ModBlocks.MACHINES.get(MachineSize.NORMAL).get().defaultBlockState();
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof MachineHost, "kinetic_machine should load as a machine, got " + loaded);
        helper.assertValueEqual(((MachineHost) loaded).getRoomId(), room, "room id");
        helper.succeed();
    }

    /** A machine saved under the plain machine id also loads with its room; with Create it loads kinetic. */
    static void plainIdKeepsRoom(GameTestHelper helper) {
        UUID room = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        tag.putString("id", TinyTunnels.id("machine").toString());
        tag.putUUID("room", room);
        BlockPos pos = helper.absolutePos(MACHINE);
        BlockState state = ModBlocks.MACHINES.get(MachineSize.NORMAL).get().defaultBlockState();
        BlockEntity loaded = BlockEntity.loadStatic(pos, state, tag, helper.getLevel().registryAccess());
        helper.assertTrue(loaded instanceof MachineHost, "machine should load as a machine, got " + loaded);
        helper.assertValueEqual(((MachineHost) loaded).getRoomId(), room, "room id");
        if (Compat.CREATE) helper.assertTrue(CreateBlocks.kineticMachineClass().isInstance(loaded), "with Create, an old machine should load kinetic");
        helper.succeed();
    }

    /** The shell repair keeps a kinetic tunnel wall (inert without Create), so no hole appears. */
    static void shellRepairKeepsKinetic(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = room.geometry().wallCenter(Direction.NORTH);
        RoomData.get(helper.getLevel().getServer()).setTunnel(room.id(), Direction.EAST, new RoomTunnel<>(ModTunnelKinds.KINETIC.get(), wall, RedstoneMode.IN));
        RoomBuilder.build(rooms, TestRooms.room(helper, machine));
        BlockState state = rooms.getBlockState(wall);
        helper.assertTrue(state.is(ModBlocks.KINETIC_TUNNEL_WALL.get()), "the kinetic tunnel wall should be built");
        helper.assertValueEqual(state.getValue(KineticTunnelWallBlock.FACE), Direction.EAST, "wall face");
        helper.assertValueEqual(state.getValue(KineticTunnelWallBlock.MODE), RedstoneMode.IN, "wall mode");
        helper.succeed();
    }

    private KineticFallbackGameTests() {}
}
