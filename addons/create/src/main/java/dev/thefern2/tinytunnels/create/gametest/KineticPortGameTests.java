package dev.thefern2.tinytunnels.create.gametest;

import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.SERVICE;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.assertEquals;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.block;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.motor;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.port;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.portEntity;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.pushInto;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.room;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.rooms;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.shaft;
import static dev.thefern2.tinytunnels.create.gametest.CreateTestRooms.speed;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.create.kinetic.KineticMode;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelBlockEntity;
import dev.thefern2.tinytunnels.create.port.KineticPortBlockEntity;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import dev.thefern2.tinytunnels.create.registry.ModItems;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * The kinetic tunnel with the port as its machine end (A6.5), ported from core's {@code KineticGameTests}; K-numbers
 * from the kinetic tunnel plan. The machine is a plain core machine; the port sits against one of its faces.
 *
 * <p>The sign rule: a creative motor inside pushing into the tunnel wall at some RPM turns the outside like a
 * creative motor on that machine face pushing outwards at the same RPM, and the other way round for IN. A Create
 * motor's speed is {@code rpm × step(facing)}, so a shaft beyond the port turns at {@code rpm × step(face)}.
 *
 * <p>The machine sits one block up in the 5x5x5 structure, so a port and a shaft fit on every side, below too.
 */
final class KineticPortGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);

    /** K1: a motor at 64 RPM inside, OUT tunnel; a shaft beyond the port turns at 64 RPM. */
    static void out(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos portPos = port(helper, MACHINE, Direction.EAST);
        BlockPos outside = outsideShaft(helper, Direction.EAST);
        helper.succeedWhen(() -> {
            KineticPortBlockEntity port = portEntity(helper, portPos);
            helper.assertTrue(port.isLinked(), "the port should be linked");
            assertEquals(helper, port.mode(), KineticMode.OUT, "the port's mode");
            assertEquals(helper, speed(helper.getLevel(), helper.absolutePos(outside)), 64f, "outside shaft speed");
            helper.assertFalse(port.isOverStressed(), "port overstressed");
        });
    }

    /**
     * K3: the sign rule on every same-side pair (wall on the side matching the face) and on pairs whose axes differ.
     * One machine and room; one pair after another, each waiting for its result.
     */
    static void sign(GameTestHelper helper) {
        CreateTestRooms.placeMachine(helper, MACHINE);
        ServerLevel rooms = rooms(helper);
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
            BlockPos[] motor = new BlockPos[1];
            BlockPos portPos = MACHINE.relative(face);
            BlockPos outside = MACHINE.relative(face, 2);
            sequence.thenExecute(() -> {
                wall[0] = CreateTestRooms.addKineticTunnel(helper, MACHINE, face, side, KineticMode.OUT);
                motor[0] = pushInto(helper, room(helper, MACHINE), wall[0], 32);
                port(helper, MACHINE, face);
                outsideShaft(helper, face);
            }).thenWaitUntil(() -> assertEquals(helper, speed(helper.getLevel(), helper.absolutePos(outside)), 32f * face.getAxisDirection().getStep(),
                    "outside speed, wall on " + side.getSerializedName() + " to face " + face.getSerializedName()))
                    .thenExecute(() -> {
                        CreateTestRooms.removeKineticTunnel(helper, MACHINE, face);
                        rooms.setBlock(motor[0], Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        helper.setBlock(outside, Blocks.AIR);
                        helper.setBlock(portPos, Blocks.AIR);
                    });
        }
        sequence.thenSucceed();
    }

    /**
     * A6.8: changing the inside motor's speed while linked carries through. 120 RPM, then 64, then -64 (the sign
     * flip): each time the outside (a millstone on the port, on the up face) follows, and the stress through the
     * tunnel matches the millstone's load at the new speed. Each step waits for the outside before the next change.
     */
    static void speedChange(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.UP, Direction.EAST, KineticMode.OUT);
        BlockPos motor = pushInto(helper, room, wall, 120);
        BlockPos portPos = port(helper, MACHINE, Direction.UP);
        // The outside is a millstone on the port (it takes its shaft from below), so the stress has to follow too.
        Block millstone = block("millstone");
        helper.setBlock(MACHINE.above(2), millstone);
        BlockPos outside = helper.absolutePos(MACHINE.above(2));
        float impact = (float) BlockStressValues.getImpact(millstone);
        ServerLevel rooms = rooms(helper);
        KineticTunnelBlockEntity wallEnd = (KineticTunnelBlockEntity) rooms.getBlockEntity(wall);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertEquals(helper, speed(helper.getLevel(), outside), 120f, "outside speed at 120 RPM inside");
                    checkStress(helper, wallEnd, portEntity(helper, portPos), impact * 120);
                })
                .thenExecute(() -> setMotor(rooms, motor, 64))
                .thenWaitUntil(() -> {
                    assertEquals(helper, speed(rooms, motor), 64f, "inside motor after the change");
                    assertEquals(helper, speed(helper.getLevel(), outside), 64f, "outside speed after 120 -> 64");
                    checkStress(helper, wallEnd, portEntity(helper, portPos), impact * 64);
                })
                .thenExecute(() -> setMotor(rooms, motor, -64))
                .thenWaitUntil(() -> {
                    assertEquals(helper, speed(rooms, motor), -64f, "inside motor after the flip");
                    assertEquals(helper, speed(helper.getLevel(), outside), -64f, "outside speed after 64 -> -64");
                    checkStress(helper, wallEnd, portEntity(helper, portPos), impact * 64);
                })
                .thenSucceed();
    }

    /**
     * A6.8, the reload case: one end is saved and unloaded while the other end's speed changes, then loads from its
     * save (as when a player inside the room comes back out). It has to take the link's current speed, not its saved
     * one. Simulated without unloading a chunk: the end's block entity is saved, removed, and put back from the tag
     * the way a chunk load does ({@code BlockEntity.loadStatic}, no neighbour updates).
     *
     * <p>First the port (OUT: the inside motor goes 120 -> 64 while the port is away), then the wall (IN: the outside
     * motor goes 120 -> 64 while the wall is away).
     */
    static void speedChangeReload(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        ServerLevel rooms = rooms(helper);
        // Part 1: the port reloads. OUT tunnel on the up face, a millstone on the port.
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.UP, Direction.EAST, KineticMode.OUT);
        BlockPos motor = pushInto(helper, room, wall, 120);
        BlockPos portPos = port(helper, MACHINE, Direction.UP);
        helper.setBlock(MACHINE.above(2), block("millstone"));
        BlockPos outside = helper.absolutePos(MACHINE.above(2));
        BlockPos portAbs = helper.absolutePos(portPos);
        Saved[] port = new Saved[1];
        // Part 2: the wall reloads. Set up after part 1 (one kinetic tunnel per machine).
        BlockPos[] inside = new BlockPos[1];
        BlockPos outsideMotor = helper.absolutePos(MACHINE.east(2));
        Saved[] wallEnd = new Saved[1];
        helper.startSequence()
                .thenWaitUntil(() -> assertEquals(helper, speed(helper.getLevel(), outside), 120f, "outside speed at 120 RPM inside"))
                .thenExecute(() -> port[0] = unload(helper.getLevel(), portAbs))
                .thenExecute(() -> setMotor(rooms, motor, 64))
                .thenExecuteAfter(5, () -> assertEquals(helper, speed(rooms, motor), 64f, "inside motor after the change"))
                .thenExecute(() -> load(helper.getLevel(), portAbs, port[0]))
                .thenWaitUntil(() -> assertEquals(helper, speed(helper.getLevel(), outside), 64f,
                        "outside speed after the port reloads (inside went 120 -> 64 while it was unloaded)"))
                .thenExecute(() -> {
                    CreateTestRooms.removeKineticTunnel(helper, MACHINE, Direction.UP);
                    rooms.setBlock(motor, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    helper.setBlock(MACHINE.above(2), Blocks.AIR);
                    helper.setBlock(portPos, Blocks.AIR);
                    // IN tunnel on east: a motor outside on the port, a shaft inside.
                    BlockPos inWall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.IN);
                    Direction inward = room(helper, MACHINE).shape().inwardNormal(inWall);
                    inside[0] = inWall.relative(inward);
                    rooms.setBlock(inside[0], shaft(inward.getAxis()), Block.UPDATE_ALL);
                    port(helper, MACHINE, Direction.EAST);
                    motor(helper.getLevel(), outsideMotor, Direction.WEST, 120);
                    wallEnd[0] = new Saved(inWall, null, null);
                })
                .thenWaitUntil(() -> assertEquals(helper, Math.abs(speed(rooms, inside[0])), 120f, "inside speed at 120 RPM outside"))
                .thenExecute(() -> wallEnd[0] = unload(rooms, wallEnd[0].pos()))
                .thenExecute(() -> setMotor(helper.getLevel(), outsideMotor, 64))
                .thenExecuteAfter(5, () -> assertEquals(helper, Math.abs(speed(helper.getLevel(), outsideMotor)), 64f, "outside motor after the change"))
                .thenExecute(() -> load(rooms, wallEnd[0].pos(), wallEnd[0]))
                .thenWaitUntil(() -> assertEquals(helper, Math.abs(speed(rooms, inside[0])), 64f,
                        "inside speed after the wall reloads (outside went 120 -> 64 while it was unloaded)"))
                .thenSucceed();
    }

    /** A block entity as a chunk saves it: its state and full tag. */
    private record Saved(BlockPos pos, @org.jspecify.annotations.Nullable BlockState state, @org.jspecify.annotations.Nullable CompoundTag tag) {}

    /** Saves the block at {@code pos} with its block entity, then removes it (shell protection off, so core doesn't repair a wall). */
    private static Saved unload(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        CompoundTag tag = level.getBlockEntity(pos).saveWithFullMetadata(level.registryAccess());
        ShellProtection.edit(() -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
        return new Saved(pos, state, tag);
    }

    /** Puts a saved block back the way a chunk load does: the state without neighbour updates, the block entity from its tag. */
    private static void load(ServerLevel level, BlockPos pos, Saved saved) {
        ShellProtection.edit(() -> {
            level.setBlock(pos, saved.state(), Block.UPDATE_CLIENTS);
            BlockEntity loaded = BlockEntity.loadStatic(pos, saved.state(), saved.tag(), level.registryAccess());
            if (loaded == null) throw new IllegalStateException("couldn't load the saved block entity at " + pos);
            level.setBlockEntity(loaded);
        });
    }

    /** The stress through the tunnel matches the load at the current speed, at both ends, and nothing is overstressed. */
    private static void checkStress(GameTestHelper helper, KineticTunnelBlockEntity wallEnd, KineticPortBlockEntity port, float load) {
        helper.assertTrue(Math.abs(wallEnd.claimedStress() - load) < 0.5f, "wall claims " + load + " SU, got " + wallEnd.claimedStress());
        helper.assertTrue(Math.abs(port.passedStress() - load) < 0.5f, "port passes " + load + " SU, got " + port.passedStress());
        helper.assertFalse(wallEnd.isOverStressed(), "room overstressed");
        helper.assertFalse(port.isOverStressed(), "outside overstressed");
    }

    /**
     * A6.8, at a player's pace: the inside motor goes 120 -> 100 -> 80 -> 64, one change every 20 ticks, through the
     * motor's scroll value ({@code generatedSpeed.setValue}, which is what Create's value panel ends in). The outside
     * is a shaft on the east port; it follows each change and ends at 64. (One change per tick breaks the shaft
     * through Create's anti-flicker rule, with or without a tunnel: see {@link #steppedControl}.)
     */
    static void speedChangeStepped(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos motor = pushInto(helper, room, wall, 120);
        port(helper, MACHINE, Direction.EAST);
        BlockPos outside = helper.absolutePos(outsideShaft(helper, Direction.EAST));
        ServerLevel rooms = rooms(helper);
        GameTestSequence sequence = helper.startSequence()
                .thenWaitUntil(() -> {
                    assertEquals(helper, speed(helper.getLevel(), outside), 120f, "outside speed at 120 RPM inside");
                });
        for (int rpm : new int[] {100, 80}) {
            sequence.thenExecute(() -> setMotor(rooms, motor, rpm))
                    .thenExecuteAfter(20, () -> assertEquals(helper, speed(helper.getLevel(), outside), (float) rpm, "outside speed at " + rpm + " RPM inside"));
        }
        sequence.thenExecute(() -> setMotor(rooms, motor, 64))
                .thenWaitUntil(() -> {
                    assertEquals(helper, speed(rooms, motor), 64f, "inside motor after scrolling down");
                    KineticPortBlockEntity port = portEntity(helper, MACHINE.east());
                    KineticTunnelBlockEntity wallEnd = (KineticTunnelBlockEntity) rooms.getBlockEntity(wall);
                    assertEquals(helper, speed(helper.getLevel(), outside), 64f, "outside speed after 120 -> 100 -> 80 -> 64 (port: speed " + port.getSpeed()
                            + ", linked " + port.isLinked() + ", source " + port.source + ", overstressed " + port.isOverStressed()
                            + "; wall: speed " + wallEnd.getSpeed() + ", overstressed " + wallEnd.isOverStressed()
                            + "; outside block " + helper.getLevel().getBlockState(outside) + ", block entity " + helper.getLevel().getBlockEntity(outside)
                            + (helper.getLevel().getBlockEntity(outside) instanceof KineticBlockEntity shaft ? ", its source " + shaft.source + ", network " + shaft.network + " vs port " + port.network : "") + ")");
                })
                .thenSucceed();
    }

    /**
     * Control for {@link #speedChangeStepped}, with no tunnel: a creative motor stepped 120 -> 64 one RPM per tick
     * with a shaft straight against it. If this shaft breaks too, the break is Create's anti-flicker rule, not ours.
     */
    static void steppedControl(GameTestHelper helper) {
        BlockPos motorRel = new BlockPos(1, 2, 2);
        BlockPos shaftRel = motorRel.east();
        BlockPos motor = helper.absolutePos(motorRel);
        BlockPos shaftPos = helper.absolutePos(shaftRel);
        motor(helper.getLevel(), motor, Direction.EAST, 120);
        helper.setBlock(shaftRel, shaft(Direction.Axis.X));
        GameTestSequence sequence = helper.startSequence()
                .thenWaitUntil(() -> assertEquals(helper, speed(helper.getLevel(), shaftPos), 120f, "shaft at 120"));
        for (int rpm = 119; rpm >= 64; rpm--) {
            int next = rpm;
            sequence.thenExecute(() -> setMotor(helper.getLevel(), motor, next)).thenIdle(1);
        }
        sequence.thenWaitUntil(() -> {
                    helper.assertFalse(helper.getLevel().getBlockState(shaftPos).isAir(), "the shaft against the motor was broken (no tunnel involved)");
                    assertEquals(helper, speed(helper.getLevel(), shaftPos), 64f, "shaft after stepping 120 -> 64");
                })
                .thenSucceed();
    }

    /**
     * A6.8, the loop the user's game ended in, built by hand (no reload): the port and the millstone it turns each
     * name the other as their source. Create then ignores any lower speed for the port. Lowering the inside motor
     * 120 -> 64 has to bring both down to 64 anyway.
     */
    static void sourceLoop(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.UP, Direction.EAST, KineticMode.OUT);
        BlockPos motor = pushInto(helper, room, wall, 120);
        BlockPos portPos = port(helper, MACHINE, Direction.UP);
        helper.setBlock(MACHINE.above(2), block("millstone"));
        BlockPos millstone = helper.absolutePos(MACHINE.above(2));
        ServerLevel rooms = rooms(helper);
        helper.startSequence()
                .thenWaitUntil(() -> assertEquals(helper, speed(helper.getLevel(), millstone), 120f, "millstone at 120 RPM inside"))
                .thenExecute(() -> {
                    KineticPortBlockEntity port = portEntity(helper, portPos);
                    port.setSource(millstone);
                    helper.assertTrue(port.hasSource(), "the port should now have the millstone as its source");
                    KineticBlockEntity mill = (KineticBlockEntity) helper.getLevel().getBlockEntity(millstone);
                    assertEquals(helper, mill.source, port.getBlockPos(), "the millstone's source is the port");
                })
                .thenExecute(() -> setMotor(rooms, motor, 64))
                .thenExecuteAfter(20, () -> {
                    assertEquals(helper, speed(rooms, motor), 64f, "inside motor after the change");
                    assertEquals(helper, speed(helper.getLevel(), millstone), 64f, "millstone after 120 -> 64 with the loop");
                    assertEquals(helper, portEntity(helper, portPos).getSpeed(), 64f, "port after 120 -> 64 with the loop");
                })
                .thenSucceed();
    }

    /** Sets a creative motor's speed the way its scroll value does (Create runs the speed change from it). */
    private static void setMotor(net.minecraft.world.level.Level level, BlockPos pos, int rpm) {
        ((com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) level.getBlockEntity(pos)).generatedSpeed.setValue(rpm);
    }

    /** K10: IN. A motor outside pushing into the port turns a shaft inside, like a motor pushing out of the wall. */
    static void in(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.IN);
        BlockPos portPos = port(helper, MACHINE, Direction.EAST);
        motor(helper.getLevel(), helper.absolutePos(MACHINE.east(2)), Direction.WEST, 32);
        Direction inward = room.shape().inwardNormal(wall);
        BlockPos inside = wall.relative(inward);
        ServerLevel rooms = rooms(helper);
        rooms.setBlock(inside, shaft(inward.getAxis()), Block.UPDATE_ALL);
        helper.succeedWhen(() -> {
            KineticPortBlockEntity port = portEntity(helper, portPos);
            assertEquals(helper, port.mode(), KineticMode.IN, "the port's mode");
            assertEquals(helper, speed(rooms, inside), 32f * inward.getAxisDirection().getStep(), "inside shaft speed");
            helper.assertFalse(port.isOverStressed(), "outside overstressed");
        });
    }

    /** An empty-hand click flips OUT to IN: both ends swap roles, so the outside motor now turns the inside. */
    static void toggle(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        ServerLevel rooms = rooms(helper);
        Direction inward = room.shape().inwardNormal(wall);
        BlockPos inside = wall.relative(inward);
        rooms.setBlock(inside, shaft(inward.getAxis()), Block.UPDATE_ALL);
        port(helper, MACHINE, Direction.EAST);
        BlockPos outside = helper.absolutePos(MACHINE.east(2));
        helper.startSequence()
                .thenExecuteAfter(5, () -> assertEquals(helper, speed(rooms, inside), 0f, "inside speed with nothing driving"))
                .thenExecute(() -> CreateTestRooms.click(helper, rooms, wall, helper.makeMockPlayer(GameType.SURVIVAL)))
                .thenWaitUntil(() -> assertEquals(helper, room(helper, MACHINE).tunnel(Direction.EAST, ModTunnelKinds.KINETIC.get()).map(t -> t.data()).orElse(null),
                        KineticMode.IN, "mode after the click"))
                .thenExecute(() -> motor(helper.getLevel(), outside, Direction.WEST, 32))
                .thenWaitUntil(() -> assertEquals(helper, speed(rooms, inside), 32f * inward.getAxisDirection().getStep(), "inside shaft speed after the flip"))
                .thenSucceed();
    }

    /**
     * K4 (and the K6 accounting): a load outside (a millstone on the port) counts against the room. The wall claims
     * the millstone's stress, both ends show it passing through, neither side is overstressed, and the outside is
     * offered the room's whole spare capacity.
     */
    static void stress(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.UP, Direction.EAST, KineticMode.OUT);
        pushInto(helper, room, wall, 16);
        BlockPos portPos = port(helper, MACHINE, Direction.UP);
        Block millstone = block("millstone");
        helper.setBlock(MACHINE.above(2), millstone);
        float load = (float) BlockStressValues.getImpact(millstone) * 16;
        KineticTunnelBlockEntity wallEnd = (KineticTunnelBlockEntity) rooms(helper).getBlockEntity(wall);
        helper.succeedWhen(() -> {
            KineticPortBlockEntity port = portEntity(helper, portPos);
            assertEquals(helper, Math.abs(speed(helper.getLevel(), helper.absolutePos(MACHINE.above(2)))), 16f, "millstone speed");
            helper.assertTrue(Math.abs(wallEnd.claimedStress() - load) < 0.5f, "wall claims the millstone's " + load + " SU, got " + wallEnd.claimedStress());
            helper.assertTrue(Math.abs(port.passedStress() - load) < 0.5f, "port shows " + load + " SU passed through, got " + port.passedStress());
            helper.assertTrue(Math.abs(wallEnd.passedStress() - load) < 0.5f, "wall shows " + load + " SU passed through, got " + wallEnd.passedStress());
            helper.assertFalse(wallEnd.isOverStressed(), "room overstressed");
            helper.assertFalse(port.isOverStressed(), "outside overstressed");
            float inside = wallEnd.getOrCreateNetwork().calculateCapacity();
            float outside = port.getOrCreateNetwork().calculateCapacity();
            helper.assertTrue(Math.abs(inside - outside) <= Math.max(1f, inside * 1e-3f), "outside capacity " + outside + " should equal the room's " + inside);
        });
    }

    /**
     * K6: a load inside the room shares the motor with the tunnel. A gearbox splits the motor between an encased fan
     * and the wall; the outside (a millstone on the port) is offered the capacity the fan leaves.
     */
    static void sharedCapacity(GameTestHelper helper) {
        CreateTestRooms.placeMachine(helper, MACHINE);
        ServerLevel rooms = rooms(helper);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.UP, Direction.EAST, KineticMode.OUT);
        BlockPos gearbox = wall.west();
        rooms.setBlock(gearbox, block("gearbox").defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.Y), Block.UPDATE_ALL);
        Block fan = block("encased_fan");
        rooms.setBlock(gearbox.north(), fan.defaultBlockState().setValue(DirectionalKineticBlock.FACING, Direction.NORTH), Block.UPDATE_ALL);
        motor(rooms, gearbox.west(), Direction.EAST, 16);
        BlockPos portPos = port(helper, MACHINE, Direction.UP);
        Block millstone = block("millstone");
        helper.setBlock(MACHINE.above(2), millstone);
        float fanLoad = (float) BlockStressValues.getImpact(fan) * 16;
        float millstoneLoad = (float) BlockStressValues.getImpact(millstone) * 16;
        KineticTunnelBlockEntity wallEnd = (KineticTunnelBlockEntity) rooms.getBlockEntity(wall);
        helper.succeedWhen(() -> {
            KineticPortBlockEntity port = portEntity(helper, portPos);
            assertEquals(helper, Math.abs(speed(rooms, gearbox.north())), 16f, "fan speed");
            assertEquals(helper, Math.abs(speed(helper.getLevel(), helper.absolutePos(MACHINE.above(2)))), 16f, "millstone speed");
            helper.assertTrue(Math.abs(wallEnd.claimedStress() - millstoneLoad) < 0.5f, "wall claims " + millstoneLoad + " SU, got " + wallEnd.claimedStress());
            float inside = wallEnd.getOrCreateNetwork().calculateCapacity();
            float outside = port.getOrCreateNetwork().calculateCapacity();
            float expected = inside - fanLoad;
            helper.assertTrue(Math.abs(outside - expected) <= Math.max(1f, inside * 1e-3f),
                    "outside capacity " + outside + " should be the room's " + inside + " less the fan's " + fanLoad);
        });
    }

    /** K7: sneak + wrench removes the tunnel; the outside stops, and the port is no longer linked. */
    static void wrenchRemove(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos portPos = port(helper, MACHINE, Direction.EAST);
        BlockPos outside = helper.absolutePos(outsideShaft(helper, Direction.EAST));
        helper.startSequence()
                .thenWaitUntil(() -> assertEquals(helper, speed(helper.getLevel(), outside), 64f, "outside speed before removal"))
                .thenExecute(() -> CreateTestRooms.wrenchClick(helper, wall, true))
                .thenWaitUntil(() -> {
                    helper.assertTrue(rooms(helper).getBlockState(wall).is(dev.thefern2.tinytunnels.registry.ModBlocks.ROOM_WALL.get()),
                            "the wall should be a plain wall again, got " + rooms(helper).getBlockState(wall));
                    assertEquals(helper, speed(helper.getLevel(), outside), 0f, "outside speed after removal");
                    helper.assertFalse(portEntity(helper, portPos).isLinked(), "the port should no longer be linked");
                })
                .thenSucceed();
    }

    /**
     * K13: the wrench moves the tunnel to the next free face (after east comes down). The east port stops and
     * unlinks; a port on the down face takes over.
     */
    static void wrenchCycle(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos eastPort = port(helper, MACHINE, Direction.EAST);
        BlockPos downPort = port(helper, MACHINE, Direction.DOWN);
        BlockPos east = helper.absolutePos(outsideShaft(helper, Direction.EAST));
        BlockPos down = helper.absolutePos(outsideShaft(helper, Direction.DOWN));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    assertEquals(helper, speed(helper.getLevel(), east), 64f, "east speed before the move");
                    helper.assertFalse(portEntity(helper, downPort).isLinked(), "the down port isn't linked before the move");
                })
                .thenExecute(() -> CreateTestRooms.wrenchClick(helper, wall, false))
                .thenWaitUntil(() -> {
                    assertEquals(helper, speed(helper.getLevel(), east), 0f, "east speed after the move");
                    helper.assertFalse(portEntity(helper, eastPort).isLinked(), "the east port unlinks after the move");
                    assertEquals(helper, speed(helper.getLevel(), down), -64f, "down speed after the move");
                })
                .thenSucceed();
    }

    /**
     * K8: the machine picked up stops the outside and the port pops off. Placed again with the same room (after
     * 5 seconds, as a player would take at least that long), with the port put back, it turns again.
     */
    static void machineReplaced(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        UUID roomId = room.id();
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos portPos = port(helper, MACHINE, Direction.EAST);
        BlockPos outside = helper.absolutePos(outsideShaft(helper, Direction.EAST));
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenWaitUntil(() -> assertEquals(helper, speed(helper.getLevel(), outside), 64f, "outside speed before pickup"))
                .thenExecute(() -> helper.getLevel().destroyBlock(pos, false))
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getBlockState(portPos).isAir(), "the port should pop off with the machine, got " + helper.getBlockState(portPos));
                    assertEquals(helper, speed(helper.getLevel(), outside), 0f, "outside speed with the machine gone");
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    level.setBlock(pos, dev.thefern2.tinytunnels.registry.ModBlocks.MACHINES.get(MachineSize.NORMAL).get().defaultBlockState(), Block.UPDATE_ALL);
                    MachineHost placed = (MachineHost) level.getBlockEntity(pos);
                    placed.core().applyImplicitRoom(roomId);
                    placed.bindOnPlace(level, MachineSize.NORMAL);
                    port(helper, MACHINE, Direction.EAST);
                })
                .thenWaitUntil(() -> {
                    assertEquals(helper, room(helper, MACHINE).id(), roomId, "the same room");
                    assertEquals(helper, speed(helper.getLevel(), outside), 64f, "outside speed after placing again");
                })
                .thenSucceed();
    }

    /** K9: a second kinetic tunnel in the same room is refused (one per machine until A7), and the item isn't used up. */
    static void onePerMachine(GameTestHelper helper) {
        CreateTestRooms.placeMachine(helper, MACHINE);
        CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        BlockPos second = room(helper, MACHINE).shape().wallCenter(Direction.WEST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.KINETIC_TUNNEL.get()));
        CreateTestRooms.click(helper, rooms(helper), second, player);
        // A refusal: give it a few ticks to (wrongly) place, then check it didn't.
        helper.startSequence().thenExecuteAfter(3, () -> {
            helper.assertTrue(rooms(helper).getBlockState(second).is(dev.thefern2.tinytunnels.registry.ModBlocks.ROOM_WALL.get()),
                    "the second wall should stay a room wall, got " + rooms(helper).getBlockState(second));
            helper.assertFalse(player.getMainHandItem().isEmpty(), "the item should not be used up");
            long kinetic = room(helper, MACHINE).tunnels().values().stream().filter(t -> t.kind() == ModTunnelKinds.KINETIC.get()).count();
            assertEquals(helper, kinetic, 1L, "kinetic tunnels in the room");
        }).thenSucceed();
    }

    /**
     * A port only links to a kinetic tunnel on the face it sits on. A port on a face without one, and a port with no
     * machine behind it, aren't linked and don't turn; the port on the tunnel's face is linked.
     */
    static void portNotLinked(GameTestHelper helper) {
        RoomView room = CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.OUT);
        pushInto(helper, room, wall, 64);
        BlockPos linked = port(helper, MACHINE, Direction.EAST);
        BlockPos otherFace = port(helper, MACHINE, Direction.WEST);
        // No machine behind: set straight into the world (placing it by hand isn't allowed there).
        BlockPos alone = new BlockPos(0, 1, 0);
        helper.setBlock(alone, ModBlocks.KINETIC_PORT.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(portEntity(helper, linked).isLinked(), "the port on the tunnel's face should link"))
                .thenExecuteAfter(5, () -> {
                    KineticPortBlockEntity west = portEntity(helper, otherFace);
                    helper.assertFalse(west.isLinked(), "a port on a face without a kinetic tunnel shouldn't link");
                    assertEquals(helper, west.getSpeed(), 0f, "that port's speed");
                    KineticPortBlockEntity lone = portEntity(helper, alone);
                    helper.assertFalse(lone.isLinked(), "a port with no machine behind shouldn't link");
                    assertEquals(helper, lone.getSpeed(), 0f, "that port's speed");
                    helper.assertTrue(lone.mode() == null, "an unlinked port has no mode, got " + lone.mode());
                })
                .thenSucceed();
    }

    /** The port only stays against a machine: when the machine goes, it pops off as an item. */
    static void portPopsOff(GameTestHelper helper) {
        CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos portPos = port(helper, MACHINE, Direction.NORTH);
        helper.startSequence()
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(MACHINE), false))
                .thenWaitUntil(() -> {
                    helper.assertTrue(helper.getBlockState(portPos).isAir(), "the port should pop off, got " + helper.getBlockState(portPos));
                    helper.assertItemEntityPresent(ModItems.KINETIC_PORT.get(), portPos, 2.0);
                })
                .thenSucceed();
    }

    /** A shaft beyond the port on {@code face}, on the face's axis. Returns its relative position. */
    private static BlockPos outsideShaft(GameTestHelper helper, Direction face) {
        BlockPos pos = MACHINE.relative(face, 2);
        helper.setBlock(pos, shaft(face.getAxis()));
        return pos;
    }

    private KineticPortGameTests() {}
}
