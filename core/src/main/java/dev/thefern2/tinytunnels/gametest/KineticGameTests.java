package dev.thefern2.tinytunnels.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;

import dev.thefern2.tinytunnels.compat.create.KineticMachineBlockEntity;
import dev.thefern2.tinytunnels.compat.create.KineticTunnelBlockEntity;
import dev.thefern2.tinytunnels.machine.MachineFaces;
import dev.thefern2.tinytunnels.machine.MachineBlockEntity;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.tunnel.CapabilityUpdates;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Kinetic tunnel GameTests (Create rotation), K-numbers from the kinetic tunnel plan. The one test class
 * outside {@code compat/create} that imports Create: {@link TinyTunnelsGameTests} only adds these when
 * Create is loaded. Create blocks are looked up by id, so compiling doesn't need Registrate.
 *
 * <p>The sign rule the tests check: a creative motor inside pushing into the tunnel wall at some RPM turns
 * the outside like a creative motor on that machine face pushing outwards at the same RPM, and the other
 * way round for IN. A Create motor's speed is {@code rpm × step(facing)}, so the expected speed of a shaft
 * on the far side is {@code rpm × step(the direction the rotation leaves in)}.
 */
final class KineticGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);

    static Map<String, TinyTunnelsGameTests.Test> tests() {
        Map<String, TinyTunnelsGameTests.Test> tests = new LinkedHashMap<>();
        tests.put("kinetic_out", new TinyTunnelsGameTests.Test(KineticGameTests::out, 100));
        tests.put("kinetic_sign", new TinyTunnelsGameTests.Test(KineticGameTests::sign, 800));
        tests.put("kinetic_stress", new TinyTunnelsGameTests.Test(KineticGameTests::stress, 100));
        tests.put("kinetic_shared_capacity", new TinyTunnelsGameTests.Test(KineticGameTests::sharedCapacity, 100));
        tests.put("kinetic_wrench_remove", new TinyTunnelsGameTests.Test(KineticGameTests::wrenchRemove, 100));
        tests.put("kinetic_wrench_cycle", new TinyTunnelsGameTests.Test(KineticGameTests::wrenchCycle, 200));
        tests.put("kinetic_machine_replaced", new TinyTunnelsGameTests.Test(KineticGameTests::machineReplaced, 400));
        tests.put("kinetic_one_per_machine", new TinyTunnelsGameTests.Test(KineticGameTests::onePerMachine, 100));
        tests.put("kinetic_plain_machine_refused", new TinyTunnelsGameTests.Test(KineticGameTests::plainMachineRefused, 100));
        tests.put("kinetic_in", new TinyTunnelsGameTests.Test(KineticGameTests::in, 100));
        tests.put("kinetic_toggle", new TinyTunnelsGameTests.Test(KineticGameTests::toggle, 200));
        tests.put("kinetic_machine_keeps_room", new TinyTunnelsGameTests.Test(KineticGameTests::keepsRoom, 100));
        return tests;
    }

    /** K1: a motor at 64 RPM inside, OUT tunnel; the machine face outside turns a shaft at 64 RPM. */
    static void out(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = addKineticTunnel(helper, room, Direction.EAST, Direction.EAST, RedstoneMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos outside = shaft(helper, MACHINE.east());
        helper.succeedWhen(() -> {
            helper.assertValueEqual(speed(helper.getLevel(), helper.absolutePos(outside)), 64f, "outside shaft speed");
            helper.assertFalse(machine.isOverStressed(), "machine overstressed");
        });
    }

    /**
     * K3: the sign rule on every same-side pair (wall on the side matching the face) and on pairs whose
     * axes differ. One machine and room, one pair after another.
     */
    static void sign(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        ServerLevel rooms = TestRooms.rooms(helper);
        List<Direction[]> pairs = new ArrayList<>();
        for (Direction face : Direction.values()) pairs.add(new Direction[] {face, face});
        pairs.add(new Direction[] {Direction.WEST, Direction.UP});
        pairs.add(new Direction[] {Direction.DOWN, Direction.NORTH});
        pairs.add(new Direction[] {Direction.NORTH, Direction.EAST});
        pairs.add(new Direction[] {Direction.UP, Direction.SOUTH});
        pairs.add(new Direction[] {Direction.SOUTH, Direction.DOWN});
        pairs.add(new Direction[] {Direction.EAST, Direction.WEST});
        GameTestSequence sequence = helper.startSequence();
        for (Direction[] pair : pairs) {
            Direction side = pair[0], face = pair[1];
            BlockPos[] wall = new BlockPos[1];
            BlockPos outside = MACHINE.relative(face);
            sequence.thenExecute(() -> {
                Room room = TestRooms.room(helper, machine);
                wall[0] = addKineticTunnel(helper, room, face, side, RedstoneMode.OUT);
                pushInto(helper, room, wall[0], 32);
                shaft(helper, outside);
            }).thenWaitUntil(() -> helper.assertValueEqual(speed(helper.getLevel(), helper.absolutePos(outside)), 32f * face.getAxisDirection().getStep(),
                    "outside speed, wall on " + side.getSerializedName() + " to face " + face.getSerializedName()
                            + " (outside " + helper.getBlockState(outside) + ", machine " + helper.getBlockState(MACHINE).getBlock() + ")"))
                    .thenExecute(() -> {
                        Room room = TestRooms.room(helper, machine);
                        removeKineticTunnel(helper, room, face, wall[0]);
                        rooms.setBlock(wall[0].relative(room.geometry().inwardNormal(wall[0])), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        helper.setBlock(outside, Blocks.AIR);
                    });
        }
        sequence.thenSucceed();
    }

    /**
     * K4 (and the K6 accounting): a load outside (a millstone) counts against the room. The wall claims the
     * millstone's stress, neither side is overstressed, and the outside is offered the room's whole spare
     * capacity: the wall's own claim isn't taken off what it passes on.
     */
    static void stress(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = addKineticTunnel(helper, room, Direction.UP, Direction.EAST, RedstoneMode.OUT);
        pushInto(helper, room, wall, 16);
        Block millstone = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "millstone"));
        helper.setBlock(MACHINE.above(), millstone);
        float load = (float) BlockStressValues.getImpact(millstone) * 16;
        KineticTunnelBlockEntity wallEnd = (KineticTunnelBlockEntity) TestRooms.rooms(helper).getBlockEntity(wall);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(Math.abs(speed(helper.getLevel(), helper.absolutePos(MACHINE.above()))), 16f, "millstone speed");
            helper.assertTrue(Math.abs(wallEnd.claimedStress() - load) < 0.5f, "wall claims the millstone's " + load + " SU, got " + wallEnd.claimedStress());
            // What Jade shows: the same stress at both ends.
            helper.assertTrue(Math.abs(machine.passedStress() - load) < 0.5f, "machine shows " + load + " SU passed through, got " + machine.passedStress());
            helper.assertTrue(Math.abs(wallEnd.passedStress() - load) < 0.5f, "wall shows " + load + " SU passed through, got " + wallEnd.passedStress());
            helper.assertFalse(wallEnd.isOverStressed(), "room overstressed");
            helper.assertFalse(machine.isOverStressed(), "outside overstressed");
            float inside = wallEnd.getOrCreateNetwork().calculateCapacity();
            float outside = machine.getOrCreateNetwork().calculateCapacity();
            helper.assertTrue(Math.abs(inside - outside) <= Math.max(1f, inside * 1e-3f), "outside capacity " + outside + " should equal the room's " + inside);
        });
    }

    /**
     * K6: a load inside the room shares the motor with the tunnel. A gearbox splits the motor between an
     * encased fan and the wall; the outside (a millstone) is offered the capacity the fan leaves, and the
     * wall still claims the millstone's stress.
     */
    static void sharedCapacity(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = addKineticTunnel(helper, room, Direction.UP, Direction.EAST, RedstoneMode.OUT);
        BlockPos gearbox = wall.west();
        rooms.setBlock(gearbox, block("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
        Block fan = block("encased_fan");
        rooms.setBlock(gearbox.north(), fan.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        motor(rooms, gearbox.west(), Direction.EAST, 16);
        Block millstone = block("millstone");
        helper.setBlock(MACHINE.above(), millstone);
        float fanLoad = (float) BlockStressValues.getImpact(fan) * 16;
        float millstoneLoad = (float) BlockStressValues.getImpact(millstone) * 16;
        KineticTunnelBlockEntity wallEnd = (KineticTunnelBlockEntity) rooms.getBlockEntity(wall);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(Math.abs(speed(rooms, gearbox.north())), 16f, "fan speed");
            helper.assertValueEqual(Math.abs(speed(helper.getLevel(), helper.absolutePos(MACHINE.above()))), 16f, "millstone speed");
            helper.assertTrue(Math.abs(wallEnd.claimedStress() - millstoneLoad) < 0.5f, "wall claims " + millstoneLoad + " SU, got " + wallEnd.claimedStress());
            float inside = wallEnd.getOrCreateNetwork().calculateCapacity();
            float outside = machine.getOrCreateNetwork().calculateCapacity();
            float expected = inside - fanLoad;
            helper.assertTrue(Math.abs(outside - expected) <= Math.max(1f, inside * 1e-3f),
                    "outside capacity " + outside + " should be the room's " + inside + " less the fan's " + fanLoad);
        });
    }

    /** K7: sneak + wrench removes the tunnel; the outside stops within a few ticks and the port goes. */
    static void wrenchRemove(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = addKineticTunnel(helper, room, Direction.EAST, Direction.EAST, RedstoneMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos outside = helper.absolutePos(shaft(helper, MACHINE.east()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(speed(helper.getLevel(), outside), 64f, "outside speed before removal"))
                .thenExecute(() -> RedstoneGameTests.wrenchClick(helper, wall, true))
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(TestRooms.rooms(helper).getBlockState(wall).is(ModBlocks.ROOM_WALL.get()), "the wall should be a plain wall again");
                    helper.assertValueEqual(speed(helper.getLevel(), outside), 0f, "outside speed after removal");
                    helper.assertFalse(TestRooms.faces(helper, MACHINE).faces().values().stream().anyMatch(face -> face.kind().equals(MachineFaces.KINETIC)), "kinetic port cleared");
                })
                .thenSucceed();
    }

    /** K13: the wrench moves the tunnel to the next free face (after east comes down); the shaft moves with it. */
    static void wrenchCycle(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = addKineticTunnel(helper, room, Direction.EAST, Direction.EAST, RedstoneMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos east = helper.absolutePos(shaft(helper, MACHINE.east()));
        BlockPos down = helper.absolutePos(shaft(helper, MACHINE.below()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(speed(helper.getLevel(), east), 64f, "east speed before the move"))
                .thenExecute(() -> RedstoneGameTests.wrenchClick(helper, wall, false))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(speed(helper.getLevel(), east), 0f, "east speed after the move");
                    helper.assertValueEqual(speed(helper.getLevel(), down), -64f, "down speed after the move");
                })
                .thenSucceed();
    }

    /**
     * K8: the machine picked up stops the outside; placed again with the same room, it turns again. It
     * waits 5 seconds before placing, as a player would, so the room chunk has time to unload.
     */
    static void machineReplaced(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        UUID roomId = machine.getRoomId();
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = addKineticTunnel(helper, room, Direction.EAST, Direction.EAST, RedstoneMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos outside = helper.absolutePos(shaft(helper, MACHINE.east()));
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(speed(helper.getLevel(), outside), 64f, "outside speed before pickup"))
                .thenExecute(() -> helper.getLevel().removeBlock(pos, false))
                .thenExecuteAfter(3, () -> helper.assertValueEqual(speed(helper.getLevel(), outside), 0f, "outside speed with the machine gone"))
                .thenIdle(100)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    level.setBlock(pos, ModBlocks.MACHINES.get(MachineSize.NORMAL).get().defaultBlockState(), Block.UPDATE_ALL);
                    MachineHost placed = (MachineHost) level.getBlockEntity(pos);
                    placed.core().applyImplicitRoom(roomId);
                    placed.bindOnPlace(level, MachineSize.NORMAL);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(speed(helper.getLevel(), outside), 64f, "outside speed after placing again" + machineInfo(helper.getLevel().getBlockEntity(pos))
                        + " wall " + kineticInfo(TestRooms.rooms(helper), wall) + " motor " + kineticInfo(TestRooms.rooms(helper), wall.relative(room.geometry().inwardNormal(wall)))))
                .thenSucceed();
    }

    /** K9: a second kinetic tunnel in the same room is refused, and the item isn't used up. */
    static void onePerMachine(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        addKineticTunnel(helper, room, Direction.EAST, Direction.EAST, RedstoneMode.OUT);
        BlockPos second = room.geometry().wallCenter(Direction.WEST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KINETIC_TUNNEL.get()));
        RedstoneGameTests.click(helper, second, player);
        helper.assertTrue(TestRooms.rooms(helper).getBlockState(second).is(ModBlocks.ROOM_WALL.get()), "the second wall should stay a room wall");
        helper.assertFalse(player.getMainHandItem().isEmpty(), "the item should not be used up");
        helper.assertValueEqual(TestRooms.room(helper, machine).count(ModTunnelKinds.KINETIC.get()), 1, "kinetic tunnels in the room");
        helper.succeed();
    }

    /** K12: a machine with a plain block entity (placed before Create) refuses the kinetic tunnel. */
    static void plainMachineRefused(GameTestHelper helper) {
        KineticMachineBlockEntity kinetic = kineticMachine(helper);
        UUID roomId = kinetic.getRoomId();
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(MACHINE);
        level.removeBlockEntity(pos);
        MachineBlockEntity plain = new MachineBlockEntity(pos, level.getBlockState(pos));
        plain.core().applyImplicitRoom(roomId);
        level.setBlockEntity(plain);
        plain.bindOnPlace(level, MachineSize.NORMAL);
        Room room = RoomData.get(level.getServer()).room(roomId).orElseThrow();
        BlockPos wall = room.geometry().wallCenter(Direction.EAST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KINETIC_TUNNEL.get()));
        RedstoneGameTests.click(helper, wall, player);
        helper.assertTrue(TestRooms.rooms(helper).getBlockState(wall).is(ModBlocks.ROOM_WALL.get()), "the wall should stay a room wall");
        helper.assertFalse(player.getMainHandItem().isEmpty(), "the item should not be used up");
        helper.succeed();
    }

    /** K10: IN. A motor outside pushing into the machine turns a shaft inside, like a motor pushing out of the wall. */
    static void in(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = addKineticTunnel(helper, room, Direction.EAST, Direction.EAST, RedstoneMode.IN);
        motor(helper.getLevel(), helper.absolutePos(MACHINE.east()), Direction.WEST, 32);
        Direction inward = room.geometry().inwardNormal(wall);
        BlockPos inside = wall.relative(inward);
        ServerLevel rooms = TestRooms.rooms(helper);
        rooms.setBlock(inside, shaftState(inward.getAxis()), Block.UPDATE_ALL);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(speed(rooms, inside), 32f * inward.getAxisDirection().getStep(), "inside shaft speed");
            helper.assertFalse(machine.isOverStressed(), "outside overstressed");
        });
    }

    /** An empty-hand click flips OUT to IN: both ends swap roles, so the outside motor now turns the inside. */
    static void toggle(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = addKineticTunnel(helper, room, Direction.EAST, Direction.EAST, RedstoneMode.OUT);
        ServerLevel rooms = TestRooms.rooms(helper);
        Direction inward = room.geometry().inwardNormal(wall);
        BlockPos inside = wall.relative(inward);
        rooms.setBlock(inside, shaftState(inward.getAxis()), Block.UPDATE_ALL);
        BlockPos outside = helper.absolutePos(MACHINE.east());
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    helper.assertValueEqual(speed(rooms, inside), 0f, "inside speed with nothing driving");
                    RedstoneGameTests.click(helper, wall, helper.makeMockPlayer(GameType.SURVIVAL));
                    motor(helper.getLevel(), outside, Direction.WEST, 32);
                })
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(TestRooms.room(helper, machine).data(Direction.EAST, ModTunnelKinds.KINETIC.get()), RedstoneMode.IN, "mode after the click");
                    helper.assertValueEqual(speed(rooms, inside), 32f * inward.getAxisDirection().getStep(), "inside shaft speed after the flip");
                })
                .thenSucceed();
    }

    /** The kinetic machine saves its room id like the plain one, so a picked-up machine keeps its room. */
    static void keepsRoom(GameTestHelper helper) {
        KineticMachineBlockEntity machine = kineticMachine(helper);
        CompoundTag tag = machine.saveWithoutMetadata(helper.getLevel().registryAccess());
        helper.assertTrue(tag.hasUUID("room"), "saved tag has the room id");
        helper.assertValueEqual(tag.getUUID("room"), machine.getRoomId(), "saved room id");
        helper.succeed();
    }

    private static KineticMachineBlockEntity kineticMachine(GameTestHelper helper) {
        MachineHost host = TestRooms.placeMachine(helper, MACHINE);
        if (!(host instanceof KineticMachineBlockEntity machine)) {
            throw new IllegalStateException("With Create loaded the machine should be kinetic, got " + host.getClass().getSimpleName());
        }
        return machine;
    }

    /** A kinetic tunnel for machine face {@code face} on the middle of the room's wall on {@code side}, as the item makes it. */
    private static BlockPos addKineticTunnel(GameTestHelper helper, Room room, Direction face, Direction side, RedstoneMode mode) {
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = room.geometry().wallCenter(side);
        Direction inward = room.geometry().inwardNormal(wall);
        ShellProtection.edit(() -> rooms.setBlock(wall, ModBlocks.KINETIC_TUNNEL_WALL.get().defaultBlockState()
                .setValue(KineticTunnelWallBlock.FACE, face).setValue(KineticTunnelWallBlock.INWARD, inward)
                .setValue(KineticTunnelWallBlock.MODE, mode), Block.UPDATE_ALL));
        RoomData data = RoomData.get(helper.getLevel().getServer());
        // One kinetic tunnel per room, as the item enforces: drop any other first.
        data.room(room.id()).orElseThrow().faces().forEach((other, tunnel) -> {
            if (tunnel.kind() == ModTunnelKinds.KINETIC.get() && other != face) data.removeTunnel(room.id(), other);
        });
        data.setTunnel(room.id(), face, new RoomTunnel<>(ModTunnelKinds.KINETIC.get(), wall, mode));
        CapabilityUpdates.syncFaces(room.id());
        return wall;
    }

    private static void removeKineticTunnel(GameTestHelper helper, Room room, Direction face, BlockPos wall) {
        ServerLevel rooms = TestRooms.rooms(helper);
        ShellProtection.edit(() -> rooms.setBlock(wall, ModBlocks.ROOM_WALL.get().defaultBlockState(), Block.UPDATE_ALL));
        RoomData.get(helper.getLevel().getServer()).removeTunnel(room.id(), face);
        CapabilityUpdates.syncFaces(room.id());
    }

    /** A creative motor inside the room, next to the wall and pushing into it. */
    private static void pushInto(GameTestHelper helper, Room room, BlockPos wall, int rpm) {
        Direction inward = room.geometry().inwardNormal(wall);
        motor(TestRooms.rooms(helper), wall.relative(inward), inward.getOpposite(), rpm);
    }

    private static Block block(String createId) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", createId));
    }

    private static void motor(Level level, BlockPos pos, Direction facing, int rpm) {
        Block motor = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        level.setBlock(pos, motor.defaultBlockState().setValue(DirectionalKineticBlock.FACING, facing), Block.UPDATE_ALL);
        ((CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(rpm);
    }

    /** A shaft next to the machine, on the axis that points at it. Returns the same relative position. */
    private static BlockPos shaft(GameTestHelper helper, BlockPos relative) {
        Direction face = Direction.fromDelta(relative.getX() - MACHINE.getX(), relative.getY() - MACHINE.getY(), relative.getZ() - MACHINE.getZ());
        helper.setBlock(relative, shaftState(face.getAxis()));
        return relative;
    }

    private static BlockState shaftState(Direction.Axis axis) {
        Block shaft = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "shaft"));
        return shaft.defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    private static String machineInfo(net.minecraft.world.level.block.entity.BlockEntity entity) {
        if (!(entity instanceof KineticMachineBlockEntity machine)) return " (machine block entity " + entity + ")";
        return " (machine speed " + machine.getSpeed() + ", generated " + machine.getGeneratedSpeed() + ", network " + machine.network
                + ", source " + machine.source + ", overstressed " + machine.isOverStressed() + ", " + machine.debugLink() + ")";
    }

    private static String kineticInfo(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) return "(not loaded)";
        if (!(level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic)) return "(" + level.getBlockState(pos) + ", no kinetic block entity)";
        return "(speed " + kinetic.getSpeed() + ", theoretical " + kinetic.getTheoreticalSpeed() + ", network " + kinetic.network + ", source " + kinetic.source
                + ", removed " + kinetic.isRemoved() + ")";
    }

    private static float speed(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic ? kinetic.getSpeed() : 0;
    }

    private KineticGameTests() {}
}
