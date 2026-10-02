package dev.thefern2.tinytunnels.create.gametest;

import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.assertEquals;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.block;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.motor;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.port;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.portEntity;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.pushInto;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.rooms;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.shaft;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.speed;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelBlockEntity;
import dev.thefern2.tinytunnels.create.kinetic.LinkedKineticBlockEntity;
import dev.thefern2.tinytunnels.create.port.KineticPortBlock;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.create.kinetic.KineticMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Several kinetic tunnels on one machine (A7.3). Two tunnels: one on the machine's east face (wall on the room's
 * east side), one on its west face (wall on the west side), each with its own port and its own outside line. They
 * need {@code maxPerRoom} above 1 (A7.1); before that, the second tunnel is refused and these fail at setup.
 *
 * <p>Speeds differ per tunnel (64 east, 32 west), so a line driven by the wrong tunnel shows. Sign rule as in
 * {@link KineticPortGameTests}: OUT gives {@code rpm × step(face)} beyond the port; IN gives
 * {@code rpm × step(inward)} on a shaft against the wall inside.
 *
 * <p>The loop guard (A7.2, "Why rotation can't loop" in the kinetic tunnel plan) is tested from {@link #loopRoomMotor} on:
 * a link may only drive if its consumer's network and its driving network share no real source. A blocked end
 * reports {@code isBlocked()}, generates 0 and claims nothing.
 *
 * <p>The joined layouts: inside, a shaft line from the west wall to the east wall, with a gearbox in the middle and,
 * if wanted, a creative motor on top of it ({@link #insideLine}); outside, a ring of shafts and gearboxes from the
 * east port round the north side to the west port, again with an optional motor on a gearbox in the middle
 * ({@link #ring}). Every placed block is checked at the end, so a break shows.
 */
final class MultiKineticGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);

    /** Two OUT tunnels, each driven by its own motor inside: each port's line turns at its own motor's speed. */
    static void outOut(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos east = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos west = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.WEST, Direction.WEST, KineticMode.OUT);
        pushInto(helper, room, east, 64);
        pushInto(helper, room, west, 32);
        port(helper, MACHINE, Direction.EAST);
        port(helper, MACHINE, Direction.WEST);
        BlockPos eastLine = outsideShaft(helper, Direction.EAST);
        BlockPos westLine = outsideShaft(helper, Direction.WEST);
        helper.succeedWhen(() -> {
            assertEquals(helper, speed(helper.getLevel(), eastLine), 64f, "east line (east tunnel, 64 RPM inside)");
            assertEquals(helper, speed(helper.getLevel(), westLine), -32f, "west line (west tunnel, 32 RPM inside)");
            helper.assertTrue(portEntity(helper, MACHINE.east()).isLinked() && portEntity(helper, MACHINE.west()).isLinked(), "both ports linked");
        });
    }

    /** Two IN tunnels, each driven by its own motor outside: each wall turns its own shaft inside. */
    static void inIn(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos east = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.IN);
        BlockPos west = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.WEST, Direction.WEST, KineticMode.IN);
        port(helper, MACHINE, Direction.EAST);
        port(helper, MACHINE, Direction.WEST);
        motor(helper.getLevel(), helper.absolutePos(MACHINE.east(2)), Direction.WEST, 64);
        motor(helper.getLevel(), helper.absolutePos(MACHINE.west(2)), Direction.EAST, 32);
        ServerLevel rooms = rooms(helper);
        Direction eastIn = room.shape().inwardNormal(east);
        Direction westIn = room.shape().inwardNormal(west);
        BlockPos eastInside = insideShaft(rooms, east, eastIn);
        BlockPos westInside = insideShaft(rooms, west, westIn);
        helper.succeedWhen(() -> {
            assertEquals(helper, speed(rooms, eastInside), 64f * eastIn.getAxisDirection().getStep(), "inside the east wall (64 RPM outside)");
            assertEquals(helper, speed(rooms, westInside), 32f * westIn.getAxisDirection().getStep(), "inside the west wall (32 RPM outside)");
        });
    }

    /** East OUT, west IN, on separate lines inside and out: each carries its own motor's rotation, the right way. */
    static void mixed(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos east = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos west = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.WEST, Direction.WEST, KineticMode.IN);
        pushInto(helper, room, east, 64);
        port(helper, MACHINE, Direction.EAST);
        port(helper, MACHINE, Direction.WEST);
        BlockPos eastLine = outsideShaft(helper, Direction.EAST);
        motor(helper.getLevel(), helper.absolutePos(MACHINE.west(2)), Direction.EAST, 32);
        ServerLevel rooms = rooms(helper);
        Direction westIn = room.shape().inwardNormal(west);
        BlockPos westInside = insideShaft(rooms, west, westIn);
        helper.succeedWhen(() -> {
            assertEquals(helper, speed(helper.getLevel(), eastLine), 64f, "east line out (64 RPM inside)");
            assertEquals(helper, speed(rooms, westInside), 32f * westIn.getAxisDirection().getStep(), "inside the west wall (32 RPM outside)");
            assertEquals(helper, portEntity(helper, MACHINE.east()).mode(), KineticMode.OUT, "east port's mode");
            assertEquals(helper, portEntity(helper, MACHINE.west()).mode(), KineticMode.IN, "west port's mode");
        });
    }

    /**
     * The loop, motor inside: A (east) OUT, B (west) IN, joined inside and outside. A drives the outside; B would
     * feed it back into the room, so B is blocked. Nothing breaks. With the motor removed, everything stops within
     * a few ticks and stays stopped (no free energy).
     */
    static void loopRoomMotor(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wallA = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos wallB = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.WEST, Direction.WEST, KineticMode.IN);
        ServerLevel rooms = rooms(helper);
        Line inside = insideLine(rooms, CreateTestRooms.room(helper, MACHINE), 64);
        Line outside = ring(helper.getLevel(), helper.absolutePos(MACHINE), 0);
        BlockPos portA = helper.absolutePos(port(helper, MACHINE, Direction.EAST));
        BlockPos portB = helper.absolutePos(port(helper, MACHINE, Direction.WEST));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertEquals(helper, Math.abs(speed(helper.getLevel(), outside.middle())), 64f, "outside line driven through A");
                    helper.assertFalse(end(rooms, wallA).isBlocked(), "A may drive");
                    helper.assertTrue(end(rooms, wallB).isBlocked(), "B would loop back into the room, so it's blocked");
                })
                .thenExecuteAfter(20, () -> {
                    intact(helper, rooms, inside, "inside");
                    intact(helper, helper.getLevel(), outside, "outside");
                    assertEquals(helper, Math.abs(speed(helper.getLevel(), outside.middle())), 64f, "outside still at 64 after a second");
                    helper.assertTrue(end(rooms, wallB).isBlocked(), "B still blocked (no flicker)");
                })
                .thenExecute(() -> rooms.setBlock(inside.motor(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
                .thenWaitUntil(() -> allStopped(helper, rooms, inside, outside, wallA, wallB, portA, portB))
                .thenExecuteAfter(20, () -> allStopped(helper, rooms, inside, outside, wallA, wallB, portA, portB))
                .thenSucceed();
    }

    /** The same loop with the motor outside instead: B drives the room, and A (which would feed it back out) is blocked. */
    static void loopOutsideMotor(GameTestHelper helper) {
        CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wallA = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos wallB = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.WEST, Direction.WEST, KineticMode.IN);
        ServerLevel rooms = rooms(helper);
        Line inside = insideLine(rooms, CreateTestRooms.room(helper, MACHINE), 0);
        Line outside = ring(helper.getLevel(), helper.absolutePos(MACHINE), 64);
        BlockPos portA = helper.absolutePos(port(helper, MACHINE, Direction.EAST));
        BlockPos portB = helper.absolutePos(port(helper, MACHINE, Direction.WEST));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertEquals(helper, Math.abs(speed(rooms, inside.middle())), 64f, "inside line driven through B");
                    helper.assertFalse(end(rooms, wallB).isBlocked(), "B may drive");
                    helper.assertTrue(end(helper.getLevel(), portA).isBlocked(), "A would loop back out, so it's blocked");
                })
                .thenExecuteAfter(20, () -> {
                    intact(helper, rooms, inside, "inside");
                    intact(helper, helper.getLevel(), outside, "outside");
                    helper.assertTrue(end(helper.getLevel(), portA).isBlocked(), "A still blocked (no flicker)");
                })
                .thenExecute(() -> helper.getLevel().setBlock(outside.motor(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
                .thenWaitUntil(() -> allStopped(helper, rooms, inside, outside, wallA, wallB, portA, portB))
                .thenExecuteAfter(20, () -> allStopped(helper, rooms, inside, outside, wallA, wallB, portA, portB))
                .thenSucceed();
    }

    /**
     * The diamond: two OUT links from one room network into one outside line. Only one may drive; the other shares
     * its root and is blocked. The outside is offered the room's capacity once, not twice.
     */
    static void diamond(GameTestHelper helper) {
        CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wallA = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos wallB = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.WEST, Direction.WEST, KineticMode.OUT);
        ServerLevel rooms = rooms(helper);
        Line inside = insideLine(rooms, CreateTestRooms.room(helper, MACHINE), 64);
        Line outside = ring(helper.getLevel(), helper.absolutePos(MACHINE), 0);
        BlockPos portA = helper.absolutePos(port(helper, MACHINE, Direction.EAST));
        BlockPos portB = helper.absolutePos(port(helper, MACHINE, Direction.WEST));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertEquals(helper, Math.abs(speed(helper.getLevel(), outside.middle())), 64f, "outside line speed");
                    boolean aBlocked = end(helper.getLevel(), portA).isBlocked();
                    boolean bBlocked = end(helper.getLevel(), portB).isBlocked();
                    helper.assertTrue(aBlocked != bBlocked, "exactly one of the two OUT links may drive (A blocked " + aBlocked + ", B blocked " + bBlocked + ")");
                    float room = ((KineticBlockEntity) rooms.getBlockEntity(inside.middle())).getOrCreateNetwork().calculateCapacity();
                    float out = end(helper.getLevel(), portA).getOrCreateNetwork().calculateCapacity();
                    helper.assertTrue(Math.abs(out - room) <= Math.max(1f, room * 1e-3f), "outside capacity " + out + " should be the room's " + room + ", not twice it");
                })
                .thenExecuteAfter(20, () -> {
                    intact(helper, rooms, inside, "inside");
                    intact(helper, helper.getLevel(), outside, "outside");
                })
                .thenSucceed();
    }

    /**
     * Two machines, each with its own motor in its own room, OUT into one shared line between them: different roots,
     * so both may drive, and the line is offered both rooms' capacity.
     */
    static void twoRoomsOneLine(GameTestHelper helper) {
        BlockPos left = new BlockPos(0, 2, 2);
        BlockPos right = new BlockPos(4, 2, 2);
        RoomView roomL = CreateTestRooms.placeMachine(helper, left);
        RoomView roomR = CreateTestRooms.placeMachine(helper, right);
        BlockPos wallL = CreateTestRooms.addKineticTunnel(helper, left, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos wallR = CreateTestRooms.addKineticTunnel(helper, right, Direction.WEST, Direction.WEST, KineticMode.OUT);
        // Both turn the shared X-axis line the same way: east face at +64, west face at -(-64).
        BlockPos motorL = pushInto(helper, roomL, wallL, 64);
        BlockPos motorR = pushInto(helper, roomR, wallR, -64);
        BlockPos portL = helper.absolutePos(port(helper, left, Direction.EAST));
        BlockPos portR = helper.absolutePos(port(helper, right, Direction.WEST));
        BlockPos middle = new BlockPos(2, 2, 2);
        helper.setBlock(middle, shaft(Direction.Axis.X));
        BlockPos line = helper.absolutePos(middle);
        ServerLevel rooms = rooms(helper);
        helper.succeedWhen(() -> {
            assertEquals(helper, speed(helper.getLevel(), line), 64f, "shared line speed");
            helper.assertFalse(end(helper.getLevel(), portL).isBlocked(), "the left room may drive");
            helper.assertFalse(end(helper.getLevel(), portR).isBlocked(), "the right room may drive");
            float capL = ((KineticBlockEntity) rooms.getBlockEntity(motorL)).getOrCreateNetwork().calculateCapacity();
            float capR = ((KineticBlockEntity) rooms.getBlockEntity(motorR)).getOrCreateNetwork().calculateCapacity();
            float out = end(helper.getLevel(), portL).getOrCreateNetwork().calculateCapacity();
            helper.assertTrue(Math.abs(out - (capL + capR)) <= Math.max(1f, (capL + capR) * 1e-3f),
                    "the line's capacity " + out + " should be both rooms' " + capL + " + " + capR);
            helper.assertFalse(helper.getLevel().getBlockState(line).isAir(), "the shared shaft is intact");
        });
    }

    /**
     * An outside motor and an OUT link on one line with a load (an encased fan): the room is charged only its share
     * of the load (its capacity over the line's), here half, with equal creative motors on both sides.
     */
    static void sharedLoad(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos portPos = helper.absolutePos(port(helper, MACHINE, Direction.EAST));
        // Beyond the port: a gearbox, the outside motor on its north side, the fan on its south side.
        BlockPos gearbox = MACHINE.east(2);
        helper.setBlock(gearbox, block("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y));
        motor(helper.getLevel(), helper.absolutePos(gearbox.north()), Direction.SOUTH, OUTSIDE_MOTOR_RPM);
        Block fan = block("encased_fan");
        helper.setBlock(gearbox.south(), fan.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        float load = (float) BlockStressValues.getImpact(fan) * 64;
        KineticTunnelBlockEntity wallEnd = (KineticTunnelBlockEntity) rooms(helper).getBlockEntity(wall);
        helper.succeedWhen(() -> {
            assertEquals(helper, Math.abs(speed(helper.getLevel(), helper.absolutePos(gearbox.south()))), 64f, "fan speed");
            helper.assertFalse(end(helper.getLevel(), portPos).isBlocked(), "the room may drive (its root differs from the motor's)");
            float share = load / 2;
            helper.assertTrue(Math.abs(wallEnd.claimedStress() - share) < Math.max(0.5f, share * 1e-2f),
                    "the room should be charged half the fan's " + load + " SU (" + share + "), got " + wallEnd.claimedStress());
            helper.assertFalse(helper.getLevel().getBlockState(helper.absolutePos(gearbox.north())).isAir(), "the outside motor is intact");
        });
    }

    /**
     * The loop through a nested machine: inside the outer machine's room sits a second machine whose own room has
     * A OUT and B IN, joined in its room (with the motor) and joined around it in the outer room. As in the plain
     * loop, A drives, B is blocked; with the motor gone, all stops. Both ends of each link are in the room dimension.
     */
    static void loopNested(GameTestHelper helper) {
        RoomView outer = CreateTestRooms.placeMachine(helper, MACHINE);
        ServerLevel rooms = rooms(helper);
        BlockPos inner = outer.shape().wallCenter(Direction.WEST).relative(Direction.EAST, 4);
        RoomView innerRoom = placeMachineAt(rooms, inner);
        BlockPos wallA = addTunnelAt(rooms, inner, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos wallB = addTunnelAt(rooms, inner, Direction.WEST, Direction.WEST, KineticMode.IN);
        Line inside = insideLine(rooms, CreateTestRooms.SERVICE.room(helper.getLevel().getServer(), innerRoom.id()).orElseThrow(), 64);
        Line around = ring(rooms, inner, 0);
        BlockPos portA = portAt(rooms, inner, Direction.EAST);
        BlockPos portB = portAt(rooms, inner, Direction.WEST);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertEquals(helper, Math.abs(speed(rooms, around.middle())), 64f, "line around the inner machine, driven through A");
                    helper.assertFalse(end(rooms, wallA).isBlocked(), "A may drive");
                    helper.assertTrue(end(rooms, wallB).isBlocked(), "B would loop back, so it's blocked");
                })
                .thenExecuteAfter(20, () -> {
                    intact(helper, rooms, inside, "inside the inner room");
                    intact(helper, rooms, around, "around the inner machine");
                })
                .thenExecute(() -> rooms.setBlock(inside.motor(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL))
                .thenWaitUntil(() -> allStopped(helper, rooms, inside, around, rooms, wallA, wallB, portA, portB))
                .thenExecuteAfter(20, () -> allStopped(helper, rooms, inside, around, rooms, wallA, wallB, portA, portB))
                .thenSucceed();
    }

    /** The outside motor's speed setting for {@link #sharedLoad}: chosen so it turns the line the same way as the port. */
    private static final int OUTSIDE_MOTOR_RPM = -64;

    /** A built line: every block placed, the middle (a gearbox), and the motor on it, or null. */
    private record Line(List<BlockPos> blocks, BlockPos middle, @Nullable BlockPos motor) {}

    /**
     * Inside the room: shafts from the west wall to the east wall along X, through the room's centre, with a gearbox
     * (axis Z) in the middle and, if {@code rpm} isn't 0, a creative motor on top of it facing down.
     */
    private static Line insideLine(ServerLevel rooms, RoomView room, int rpm) {
        BlockPos from = room.shape().wallCenter(Direction.WEST).relative(Direction.EAST);
        BlockPos to = room.shape().wallCenter(Direction.EAST).relative(Direction.WEST);
        BlockPos middle = new BlockPos((from.getX() + to.getX()) / 2, from.getY(), from.getZ());
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = from.getX(); x <= to.getX(); x++) {
            BlockPos pos = new BlockPos(x, from.getY(), from.getZ());
            rooms.setBlock(pos, pos.equals(middle) ? gearbox(Direction.Axis.Z) : shaft(Direction.Axis.X), Block.UPDATE_ALL);
            blocks.add(pos);
        }
        BlockPos motor = null;
        if (rpm != 0) {
            motor = middle.above();
            motor(rooms, motor, Direction.DOWN, rpm);
            blocks.add(motor);
        }
        return new Line(blocks, middle, motor);
    }

    /**
     * Outside a machine (in any level): a ring from beyond its east port, round its north side, to beyond its west
     * port. Gearboxes (axis Y) at the corners, shafts between; the middle of the north side is a gearbox (axis Z) with,
     * if {@code rpm} isn't 0, a creative motor on top facing down.
     */
    private static Line ring(ServerLevel level, BlockPos machine, int rpm) {
        List<BlockPos> blocks = new ArrayList<>();
        BlockPos east = machine.east(2), west = machine.west(2);
        BlockPos northEast = east.north(2), northWest = west.north(2), middle = machine.north(2);
        place(level, blocks, east, gearbox(Direction.Axis.Y));
        place(level, blocks, east.north(), shaft(Direction.Axis.Z));
        place(level, blocks, northEast, gearbox(Direction.Axis.Y));
        place(level, blocks, northEast.west(), shaft(Direction.Axis.X));
        place(level, blocks, middle, gearbox(Direction.Axis.Z));
        place(level, blocks, northWest.east(), shaft(Direction.Axis.X));
        place(level, blocks, northWest, gearbox(Direction.Axis.Y));
        place(level, blocks, west.north(), shaft(Direction.Axis.Z));
        place(level, blocks, west, gearbox(Direction.Axis.Y));
        BlockPos motor = null;
        if (rpm != 0) {
            motor = middle.above();
            motor(level, motor, Direction.DOWN, rpm);
            blocks.add(motor);
        }
        return new Line(blocks, middle, motor);
    }

    private static void place(ServerLevel level, List<BlockPos> blocks, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, Block.UPDATE_ALL);
        blocks.add(pos);
    }

    private static BlockState gearbox(Direction.Axis axis) {
        return block("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, axis);
    }

    /** No block of the line was broken (Create breaks blocks on conflicting speeds or flicker). */
    private static void intact(GameTestHelper helper, ServerLevel level, Line line, String what) {
        for (BlockPos pos : line.blocks()) {
            if (pos.equals(line.motor()) && level.getBlockState(pos).isAir()) continue;
            helper.assertFalse(level.getBlockState(pos).isAir(), what + ": the block at " + pos.toShortString() + " was broken");
        }
    }

    private static void allStopped(GameTestHelper helper, ServerLevel rooms, Line inside, Line outside,
                                   BlockPos wallA, BlockPos wallB, BlockPos portA, BlockPos portB) {
        allStopped(helper, rooms, inside, outside, helper.getLevel(), wallA, wallB, portA, portB);
    }

    /** With no real source left, every link end and both lines stand still. */
    private static void allStopped(GameTestHelper helper, ServerLevel rooms, Line inside, Line outside, ServerLevel outsideLevel,
                                   BlockPos wallA, BlockPos wallB, BlockPos portA, BlockPos portB) {
        assertEquals(helper, speed(rooms, inside.middle()), 0f, "inside line");
        assertEquals(helper, speed(outsideLevel, outside.middle()), 0f, "outside line");
        assertEquals(helper, end(rooms, wallA).getSpeed(), 0f, "wall A");
        assertEquals(helper, end(rooms, wallB).getSpeed(), 0f, "wall B");
        assertEquals(helper, end(outsideLevel, portA).getSpeed(), 0f, "port A");
        assertEquals(helper, end(outsideLevel, portB).getSpeed(), 0f, "port B");
    }

    /** The link end at {@code pos}; a test failure (not a crash) if it's gone, for example a port that popped off. */
    private static LinkedKineticBlockEntity end(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LinkedKineticBlockEntity end) return end;
        throw new GameTestAssertException("no kinetic link end at " + pos.toShortString() + " in " + level.dimension().location() + ": " + level.getBlockState(pos));
    }

    /** A machine placed and bound in any level (here, inside another room); returns its room. */
    private static RoomView placeMachineAt(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, dev.thefern2.tinytunnels.registry.ModBlocks.MACHINES.get(MachineSize.NORMAL).get().defaultBlockState(), Block.UPDATE_ALL);
        ((MachineHost) level.getBlockEntity(pos)).bindOnPlace(level, MachineSize.NORMAL);
        return CreateTestRooms.SERVICE.machineAt(level, pos).flatMap(machine -> machine.hostedRoom()).orElseThrow();
    }

    /** {@link CreateTestRooms#addKineticTunnel} for a machine at an absolute position in any level. */
    private static BlockPos addTunnelAt(ServerLevel level, BlockPos machine, Direction face, Direction side, KineticMode mode) {
        RoomView room = CreateTestRooms.SERVICE.machineAt(level, machine).flatMap(m -> m.hostedRoom()).orElseThrow();
        BlockPos wall = room.shape().wallCenter(side);
        PlaceResult result = CreateTestRooms.SERVICE.place(CreateTestRooms.SERVICE.roomLevel(level.getServer()), wall, ModTunnelKinds.KINETIC.get(), mode, null);
        if (!(result instanceof PlaceResult.Placed placed)) throw new IllegalStateException("kinetic tunnel not placed: " + result);
        if (placed.face() != face) {
            RoomView now = CreateTestRooms.SERVICE.room(level.getServer(), room.id()).orElseThrow();
            if (!CreateTestRooms.SERVICE.move(now, placed.face(), face)) throw new IllegalStateException("couldn't move the tunnel to " + face);
        }
        return wall;
    }

    /** A port against a machine at an absolute position in any level. Returns the port's position. */
    private static BlockPos portAt(ServerLevel level, BlockPos machine, Direction face) {
        BlockPos pos = machine.relative(face);
        level.setBlock(pos, ModBlocks.KINETIC_PORT.get().defaultBlockState().setValue(KineticPortBlock.FACING, face), Block.UPDATE_ALL);
        return pos;
    }

    /** A shaft beyond the port on {@code face}, on the face's axis. Returns its absolute position. */
    private static BlockPos outsideShaft(GameTestHelper helper, Direction face) {
        BlockPos pos = MACHINE.relative(face, 2);
        helper.setBlock(pos, shaft(face.getAxis()));
        return helper.absolutePos(pos);
    }

    /** A shaft inside the room against {@code wall}. Returns its position in the room dimension. */
    private static BlockPos insideShaft(ServerLevel rooms, BlockPos wall, Direction inward) {
        BlockPos pos = wall.relative(inward);
        rooms.setBlock(pos, shaft(inward.getAxis()), Block.UPDATE_ALL);
        return pos;
    }

    private MultiKineticGameTests() {}
}
