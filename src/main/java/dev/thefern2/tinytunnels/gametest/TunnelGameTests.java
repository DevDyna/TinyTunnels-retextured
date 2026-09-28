package dev.thefern2.tinytunnels.gametest;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.thefern2.tinytunnels.loading.RoomTickets;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EmptyEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler;

/**
 * Automated checks for tunnels, loading and the room shell. Each maps to a failure Compact Machines
 * had (see docs/research/compact-machines-tunnel-issues.md). The machine under test stands at
 * {@link #MACHINE} in a 5x5x5 empty structure; rooms are built in the room dimension.
 */
final class TunnelGameTests {
    static final BlockPos MACHINE = new BlockPos(2, 2, 2);

    /** Every machine face routed through a tunnel on every wall, both directions. */
    static void faceMatrix(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        for (Direction face : Direction.values()) {
            helper.getLevel().setBlock(machinePos.relative(face), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        }

        for (Direction face : Direction.values()) {
            for (Direction wall : Direction.values()) {
                BlockPos wallPos = TestRooms.wallCenter(room.geometry(), wall);
                BlockPos inside = TestRooms.addTunnel(helper, room, face, wallPos);
                rooms.setBlock(inside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
                String where = "face " + face.getSerializedName() + " via " + wall.getSerializedName() + " wall";

                IItemHandler in = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machinePos, face);
                helper.assertTrue(in != null, "no item handler on machine " + where);
                helper.assertValueEqual(TestRooms.insert(in, cobble(1)), 1, "inserted into machine " + where);
                helper.assertValueEqual(count(rooms, inside), 1, "items in the chest inside, " + where);

                IItemHandler out = rooms.getCapability(Capabilities.ItemHandler.BLOCK, wallPos, wall.getOpposite());
                helper.assertTrue(out != null, "no item handler on tunnel " + where);
                int before = count(helper.getLevel(), machinePos.relative(face));
                helper.assertValueEqual(TestRooms.insert(out, cobble(1)), 1, "inserted into tunnel " + where);
                helper.assertValueEqual(count(helper.getLevel(), machinePos.relative(face)), before + 1, "items in the chest outside, " + where);

                rooms.setBlock(inside, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                TestRooms.removeTunnel(helper, room, face);
            }
        }
        helper.succeed();
    }

    /** A hopper pushing into the machine lands items in the chest inside (push only, nothing pulls). */
    static void hopperOutsideIn(GameTestHelper helper) {
        BlockPos machineRel = new BlockPos(2, 1, 2);
        MachineHost machine = TestRooms.placeMachine(helper, machineRel);
        Room room = TestRooms.room(helper, machine);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.UP, TestRooms.wallCenter(room.geometry(), Direction.NORTH));
        TestRooms.rooms(helper).setBlock(inside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        helper.setBlock(machineRel.above(), Blocks.HOPPER);
        ((Container) helper.getLevel().getBlockEntity(helper.absolutePos(machineRel.above()))).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.succeedWhen(() -> helper.assertTrue(count(TestRooms.rooms(helper), inside) >= 4, "waiting for items in the chest inside"));
    }

    /** A hopper inside pushing into a tunnel lands items in the chest touching that machine face outside. */
    static void hopperInsideOut(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        helper.setBlock(MACHINE.below(), Blocks.CHEST);
        BlockPos floorTunnel = TestRooms.wallCenter(room.geometry(), Direction.DOWN);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.DOWN, floorTunnel);
        ServerLevel rooms = TestRooms.rooms(helper);
        rooms.setBlock(inside, Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
        ((Container) rooms.getBlockEntity(inside)).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        BlockPos chest = helper.absolutePos(MACHINE.below());
        helper.succeedWhen(() -> helper.assertTrue(count(helper.getLevel(), chest) >= 4, "waiting for items in the chest outside"));
    }

    /** Water through a tunnel into a cauldron inside. */
    static void fluidIntoCauldron(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.EAST, TestRooms.wallCenter(room.geometry(), Direction.WEST));
        ServerLevel rooms = TestRooms.rooms(helper);
        rooms.setBlock(inside, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
        IFluidHandler fluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(MACHINE), Direction.EAST);
        helper.assertTrue(fluid != null, "no fluid handler on the machine's east face");
        helper.assertValueEqual(TestRooms.fill(fluid, new FluidStack(Fluids.WATER, 1000)), 1000, "water inserted");
        helper.assertTrue(rooms.getBlockState(inside).is(Blocks.WATER_CAULDRON), "cauldron inside should be full of water");
        helper.succeed();
    }

    /** A full water cauldron inside drains through the machine face: the whole 1000 mB comes out and it empties. */
    static void fluidOutOfCauldron(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.EAST, TestRooms.wallCenter(room.geometry(), Direction.WEST));
        ServerLevel rooms = TestRooms.rooms(helper);
        rooms.setBlock(inside, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL), Block.UPDATE_ALL);
        IFluidHandler fluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(MACHINE), Direction.EAST);
        helper.assertTrue(fluid != null, "no fluid handler on the machine's east face");
        FluidStack simulated = fluid.drain(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.SIMULATE);
        helper.assertValueEqual(simulated.getAmount(), 1000, "water the simulated drain reports");
        helper.assertTrue(rooms.getBlockState(inside).is(Blocks.WATER_CAULDRON), "a simulated drain must leave the cauldron full");
        FluidStack drained = fluid.drain(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(drained.getFluid() == Fluids.WATER, "drained fluid should be water");
        helper.assertValueEqual(drained.getAmount(), 1000, "water drained");
        helper.assertTrue(rooms.getBlockState(inside).is(Blocks.CAULDRON), "cauldron inside should be empty");
        helper.succeed();
    }

    /** A fill the block inside can't take (500 mB into a cauldron) reports 0 and changes nothing. */
    static void fluidPartialRefused(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.EAST, TestRooms.wallCenter(room.geometry(), Direction.WEST));
        ServerLevel rooms = TestRooms.rooms(helper);
        rooms.setBlock(inside, Blocks.CAULDRON.defaultBlockState(), Block.UPDATE_ALL);
        IFluidHandler fluid = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(MACHINE), Direction.EAST);
        helper.assertTrue(fluid != null, "no fluid handler on the machine's east face");
        FluidStack half = new FluidStack(Fluids.WATER, 500);
        helper.assertValueEqual(fluid.fill(half, IFluidHandler.FluidAction.SIMULATE), 0, "simulated fill of 500 mB");
        helper.assertValueEqual(fluid.fill(half, IFluidHandler.FluidAction.EXECUTE), 0, "fill of 500 mB");
        helper.assertTrue(rooms.getBlockState(inside).is(Blocks.CAULDRON), "cauldron inside should still be empty");
        helper.succeed();
    }

    /** A tunnel face with nothing behind it answers empty (pipes connect); a face without a tunnel answers null. */
    static void emptyVersusNull(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        TestRooms.addTunnel(helper, room, Direction.UP, TestRooms.wallCenter(room.geometry(), Direction.NORTH));
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, pos, Direction.UP) == EmptyEnergyStorage.INSTANCE,
                "tunnel face with nothing behind should be the empty energy handler");
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP) == EmptyItemHandler.INSTANCE,
                "tunnel face with nothing behind should be the empty item handler");
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.DOWN) == null,
                "a face without a tunnel should expose nothing");
        helper.succeed();
    }

    /** A pipe that cached "nothing" before the tunnel existed is told when the tunnel appears. */
    static void pipeBeforeTunnel(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        AtomicBoolean notified = new AtomicBoolean();
        BlockCapabilityCache<IItemHandler, Direction> pipe = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK,
                helper.getLevel(), helper.absolutePos(MACHINE), Direction.NORTH, () -> true, () -> notified.set(true));
        helper.assertTrue(pipe.getCapability() == null, "no tunnel yet, so nothing on the north face");

        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.NORTH, TestRooms.wallCenter(room.geometry(), Direction.EAST));
        TestRooms.rooms(helper).setBlock(inside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(notified.get(), "the pipe's cache was never invalidated");
            IItemHandler handler = pipe.getCapability();
            helper.assertTrue(handler != null, "the pipe still sees nothing after the tunnel was added");
            helper.assertValueEqual(TestRooms.insert(handler, cobble(1)), 1, "inserted after the tunnel appeared");
            helper.assertValueEqual(count(TestRooms.rooms(helper), inside), 1, "items in the chest inside");
            helper.succeed();
        });
    }

    /** Swapping the block behind a tunnel re-routes a pipe without re-placing it. */
    static void hotSwap(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.SOUTH, TestRooms.wallCenter(room.geometry(), Direction.NORTH));
        ServerLevel rooms = TestRooms.rooms(helper);
        rooms.setBlock(inside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        AtomicBoolean notified = new AtomicBoolean();
        BlockCapabilityCache<IItemHandler, Direction> pipe = BlockCapabilityCache.create(Capabilities.ItemHandler.BLOCK,
                helper.getLevel(), helper.absolutePos(MACHINE), Direction.SOUTH, () -> true, () -> notified.set(true));

        helper.runAfterDelay(3, () -> {
            helper.assertTrue(pipe.getCapability() != null, "chest behind the tunnel should be reachable");
            rooms.setBlock(inside, Blocks.BARREL.defaultBlockState(), Block.UPDATE_ALL);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(notified.get(), "the pipe wasn't told the block behind the tunnel changed");
                helper.assertValueEqual(TestRooms.insert(pipe.getCapability(), cobble(1)), 1, "inserted after the swap");
                helper.assertValueEqual(count(rooms, inside), 1, "items in the barrel");
                helper.succeed();
            });
        });
    }

    /** A simulated insert reports what would go in, and moves nothing on either side. */
    static void simulateHasNoSideEffects(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.WEST, TestRooms.wallCenter(room.geometry(), Direction.SOUTH));
        TestRooms.rooms(helper).setBlock(inside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MACHINE), Direction.WEST);
        ItemStack remainder = ItemHandlerHelper.insertItem(handler, cobble(5), true);
        helper.assertValueEqual(remainder.getCount(), 0, "items left over from the simulated insert");
        helper.assertValueEqual(count(TestRooms.rooms(helper), inside), 0, "items in the chest after the simulated insert");
        helper.succeed();
    }

    /** With the machine gone, a tunnel's inward side answers empty (not null); other sides answer null. */
    static void machineMissing(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = TestRooms.wallCenter(room.geometry(), Direction.EAST);
        TestRooms.addTunnel(helper, room, Direction.NORTH, wall);
        helper.setBlock(MACHINE, Blocks.AIR);
        ServerLevel rooms = TestRooms.rooms(helper);
        helper.assertTrue(rooms.getCapability(Capabilities.ItemHandler.BLOCK, wall, Direction.WEST) == EmptyItemHandler.INSTANCE,
                "tunnel of an unplaced machine should answer empty on its inward side");
        helper.assertTrue(rooms.getCapability(Capabilities.ItemHandler.BLOCK, wall, Direction.EAST) == null,
                "tunnel should expose nothing on its outer side");
        helper.succeed();
    }

    /** Machines nested three deep pass items all the way through. */
    static void nestedChain(GameTestHelper helper) {
        BlockPos chest = buildChain(helper, 3);
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MACHINE), Direction.NORTH);
        helper.assertValueEqual(TestRooms.insert(handler, cobble(1)), 1, "inserted through three nested machines");
        helper.assertValueEqual(count(TestRooms.rooms(helper), chest), 1, "items in the innermost chest");
        helper.succeed();
    }

    /** Nesting deeper than the proxy limit moves nothing and doesn't crash. */
    static void depthLimit(GameTestHelper helper) {
        BlockPos chest = buildChain(helper, 12);
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MACHINE), Direction.NORTH);
        helper.assertTrue(handler != null, "the outer machine should still answer");
        helper.assertValueEqual(TestRooms.insert(handler, cobble(1)), 0, "inserted through twelve nested machines");
        helper.assertValueEqual(count(TestRooms.rooms(helper), chest), 0, "items in the innermost chest");
        helper.succeed();
    }

    /**
     * Machine 0 stands in the test area; each room gets a tunnel on its south wall and the next
     * machine stands against it, its south face touching the tunnel. Returns the chest in the last room.
     */
    private static BlockPos buildChain(GameTestHelper helper, int machines) {
        ServerLevel rooms = TestRooms.rooms(helper);
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        BlockPos inside = null;
        for (int i = 0; i < machines; i++) {
            Room room = TestRooms.room(helper, machine);
            Direction face = i == 0 ? Direction.NORTH : Direction.SOUTH;
            inside = TestRooms.addTunnel(helper, room, face, TestRooms.wallCenter(room.geometry(), Direction.SOUTH));
            if (i < machines - 1) machine = TestRooms.placeMachine(rooms, inside, MachineSize.NORMAL);
        }
        rooms.setBlock(inside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        return inside;
    }

    /** A wall replaced by a command comes back on the next tick. */
    static void shellRepair(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos wall = TestRooms.wallCenter(room.geometry(), Direction.EAST);
        ServerLevel rooms = TestRooms.rooms(helper);
        rooms.setBlock(wall, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(rooms.getBlockState(wall).is(ModBlocks.ROOM_WALL.get()), "the wall should have been put back");
            helper.succeed();
        });
    }

    /** A placed machine gets its room loaded; removing the machine releases it. */
    static void followTheHost(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        var roomId = machine.getRoomId();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(RoomTickets.active().contains(roomId), "waiting for the room ticket"))
                .thenExecute(() -> helper.setBlock(MACHINE, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertFalse(RoomTickets.active().contains(roomId), "waiting for the room ticket to be released"))
                .thenSucceed();
    }

    /** The machine's face flag (letter overlay, client answers) follows its tunnels. */
    static void faceFlags(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        TestRooms.addTunnel(helper, room, Direction.UP, TestRooms.wallCenter(room.geometry(), Direction.NORTH));
        BlockPos pos = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(MachineBlock.hasTunnel(helper.getLevel().getBlockState(pos), Direction.UP), "waiting for the up flag"))
                .thenExecute(() -> TestRooms.removeTunnel(helper, room, Direction.UP))
                .thenWaitUntil(() -> helper.assertFalse(MachineBlock.hasTunnel(helper.getLevel().getBlockState(pos), Direction.UP), "waiting for the up flag to clear"))
                .thenSucceed();
    }

    /** A machine can't be placed inside its own room. */
    static void noNestingInItself(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        RoomGeometry geometry = room.geometry();
        GlobalPos insideItsOwnRoom = GlobalPos.of(RoomDimension.key(helper.getLevel().getServer()), geometry.min().offset(2, 1, 2));
        RoomData data = RoomData.get(helper.getLevel().getServer());
        var roomDimension = RoomDimension.key(helper.getLevel().getServer());
        helper.assertTrue(data.wouldNestInItself(room.id(), insideItsOwnRoom, roomDimension), "placing inside its own room should be refused");
        helper.assertFalse(data.wouldNestInItself(room.id(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(MACHINE.above())), roomDimension),
                "placing next to the machine, outside any room, is fine");
        helper.succeed();
    }

    private static ItemStack cobble(int count) {
        return new ItemStack(Items.COBBLESTONE, count);
    }

    private static int count(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Container container ? container.countItem(Items.COBBLESTONE) : -1;
    }

    private TunnelGameTests() {}
}
