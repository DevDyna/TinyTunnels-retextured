package dev.thefern2.tinytunnels.gametest;

import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.tunnel.TunnelBlockEntity;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.energy.EmptyEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Buffered tunnels (docs/plans/tiny-tunnels-buffered-tunnels.md): one-way tunnels that hold a little,
 * so a pipe that only pushes and a pipe that only pulls can meet through a tunnel.
 */
final class BufferedTunnelGameTests {
    private static final BlockPos MACHINE = TunnelGameTests.MACHINE;

    /** A machine with one tunnel for face {@code face} on the room's {@code wall}, set to {@code mode}. */
    private record Setup(ServerLevel rooms, Room room, BlockPos machine, BlockPos wall, BlockPos inside, Direction inward, TunnelBlockEntity tunnel) {}

    private static Setup setup(GameTestHelper helper, Direction face, Direction wall, TunnelMode mode) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        BlockPos wallPos = TestRooms.wallCenter(room.geometry(), wall);
        BlockPos inside = TestRooms.addTunnel(helper, room, face, wallPos);
        ServerLevel rooms = TestRooms.rooms(helper);
        TunnelBlockEntity tunnel = (TunnelBlockEntity) rooms.getBlockEntity(wallPos);
        Room current = RoomData.get(helper.getLevel().getServer()).room(room.id()).orElseThrow();
        tunnel.setMode(rooms, current, mode);
        return new Setup(rooms, current, helper.absolutePos(MACHINE), wallPos, inside, room.geometry().inwardNormal(wallPos), tunnel);
    }

    private static IItemHandler faceItems(GameTestHelper helper, Setup s, Direction face) {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, s.machine(), face);
    }

    private static IItemHandler wallItems(Setup s) {
        return s.rooms().getCapability(Capabilities.ItemHandler.BLOCK, s.wall(), s.inward());
    }

    private static int buffered(Setup s) {
        return s.tunnel().buffer().contents().stream().mapToInt(ItemStack::getCount).sum();
    }

    /** BT-1: a hopper outside pushes into the machine; a hopper inside under a ceiling tunnel passes it to a chest. */
    static void itemsIn(GameTestHelper helper) {
        Setup s = setup(helper, Direction.UP, Direction.UP, TunnelMode.BUFFERED_IN);
        s.rooms().setBlock(s.inside(), Blocks.HOPPER.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos chest = s.inside().below();
        s.rooms().setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        helper.setBlock(MACHINE.above(), Blocks.HOPPER);
        ((Container) helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE.above()))).setItem(0, new ItemStack(Items.COBBLESTONE, 16));
        helper.succeedWhen(() -> helper.assertValueEqual(((Container) s.rooms().getBlockEntity(chest)).countItem(Items.COBBLESTONE), 16,
                "cobblestone in the chest inside"));
    }

    /** BT-1b: the draining end is a real container a pipe can pull from. */
    static void wallCanBePulledFrom(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        helper.assertValueEqual(TestRooms.insert(faceItems(helper, s, Direction.NORTH), new ItemStack(Items.COBBLESTONE, 10)), 10, "inserted into the machine face");
        ItemStack pulled = wallItems(s).extractItem(0, 64, false);
        helper.assertValueEqual(pulled.getCount(), 10, "pulled from the tunnel wall");
        helper.assertValueEqual(buffered(s), 0, "left in the buffer");
        helper.succeed();
    }

    /** BT-2: fluid in through the machine face, pulled out at the wall. */
    static void fluidIn(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        IFluidHandler face = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, s.machine(), Direction.NORTH);
        helper.assertValueEqual(TestRooms.fill(face, new FluidStack(Fluids.WATER, 1000)), 1000, "water into the machine face");
        IFluidHandler wall = s.rooms().getCapability(Capabilities.FluidHandler.BLOCK, s.wall(), s.inward());
        FluidStack drained = wall.drain(1000, IFluidHandler.FluidAction.EXECUTE);
        helper.assertValueEqual(drained.getAmount(), 1000, "water pulled from the tunnel wall");
        helper.assertTrue(s.tunnel().buffer().fluidContents().isEmpty(), "the buffer should be empty");
        helper.succeed();
    }

    /** BT-3: with a chest directly inside, the buffer pushes into it with nothing pulling. */
    static void activePush(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        s.rooms().setBlock(s.inside(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        TestRooms.insert(faceItems(helper, s, Direction.NORTH), new ItemStack(Items.COBBLESTONE, 10));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(((Container) s.rooms().getBlockEntity(s.inside())).countItem(Items.COBBLESTONE), 10, "cobblestone pushed into the chest");
            helper.assertValueEqual(buffered(s), 0, "left in the buffer");
        });
    }

    /** BT-4: with nothing draining it, the buffer takes up to its size, then refuses; nothing is lost. */
    static void full(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        IItemHandler face = faceItems(helper, s, Direction.NORTH);
        ItemStack left = ItemHandlerHelper.insertItem(face, new ItemStack(Items.COBBLESTONE, 100), false);
        helper.assertValueEqual(buffered(s), 64, "held in the buffer");
        helper.assertValueEqual(left.getCount(), 36, "refused and handed back");
        helper.assertValueEqual(ItemHandlerHelper.insertItem(face, new ItemStack(Items.COBBLESTONE, 1), false).getCount(), 1, "refused when full");
        helper.succeed();
    }

    /** BT-5: each end only works one way. */
    static void oneWay(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        TestRooms.insert(faceItems(helper, s, Direction.NORTH), new ItemStack(Items.COBBLESTONE, 5));
        helper.assertTrue(faceItems(helper, s, Direction.NORTH).extractItem(0, 64, false).isEmpty(), "buffered in: the machine face can't be pulled from");
        helper.assertValueEqual(ItemHandlerHelper.insertItem(wallItems(s), new ItemStack(Items.COBBLESTONE, 3), false).getCount(), 3,
                "buffered in: the wall can't be pushed into");
        s.tunnel().setMode(s.rooms(), s.room(), TunnelMode.BUFFERED_OUT);
        helper.assertValueEqual(ItemHandlerHelper.insertItem(faceItems(helper, s, Direction.NORTH), new ItemStack(Items.COBBLESTONE, 3), false).getCount(), 3,
                "buffered out: the machine face can't be pushed into");
        helper.assertValueEqual(faceItems(helper, s, Direction.NORTH).extractItem(0, 64, false).getCount(), 5,
                "buffered out: what's in the buffer comes out of the machine face");
        helper.succeed();
    }

    /** BT-6: an empty hand cycles the mode; it won't go back to pass-through while holding something. */
    static void modeCycle(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.PASSTHROUGH);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        click(s, player);
        helper.assertValueEqual(s.tunnel().mode(), TunnelMode.BUFFERED_IN, "after one click");
        click(s, player);
        helper.assertValueEqual(s.tunnel().mode(), TunnelMode.BUFFERED_OUT, "after two clicks");
        s.tunnel().setMode(s.rooms(), s.room(), TunnelMode.BUFFERED_IN);
        TestRooms.insert(faceItems(helper, s, Direction.NORTH), new ItemStack(Items.COBBLESTONE, 5));
        click(s, player);
        helper.assertValueEqual(s.tunnel().mode(), TunnelMode.BUFFERED_OUT, "in to out is allowed with contents");
        click(s, player);
        helper.assertValueEqual(s.tunnel().mode(), TunnelMode.BUFFERED_OUT, "out to pass-through is refused while not empty");
        helper.assertValueEqual(buffered(s), 5, "contents kept");
        helper.succeed();
    }

    /** BT-7: wrench removal hands the items back; with fluid inside, the first click warns and the second discards it. */
    static void removal(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        IFluidHandler face = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, s.machine(), Direction.NORTH);
        TestRooms.fill(face, new FluidStack(Fluids.WATER, 500));
        TestRooms.insert(faceItems(helper, s, Direction.NORTH), new ItemStack(Items.COBBLESTONE, 7));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        wrenchClick(player, s.wall());
        helper.assertTrue(s.rooms().getBlockState(s.wall()).is(ModBlocks.TUNNEL_WALL.get()), "the first click only warns while holding fluid");
        helper.assertValueEqual(s.tunnel().buffer().fluidContents().getAmount(), 500, "fluid kept after the warning");
        wrenchClick(player, s.wall());
        helper.assertTrue(s.rooms().getBlockState(s.wall()).is(ModBlocks.ROOM_WALL.get()), "the second click removes the tunnel");
        helper.assertValueEqual(player.getInventory().countItem(Items.COBBLESTONE), 7, "buffered cobblestone handed back");
        helper.assertValueEqual(player.getInventory().countItem(ModItems.TUNNEL.get()), 1, "the tunnel item handed back");
        helper.succeed();
    }

    /** BT-11: a bucket empties the buffer 1000 mB at a time, and fills it back. */
    static void bucket(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        TestRooms.fill(helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, s.machine(), Direction.NORTH), new FluidStack(Fluids.WATER, 1500));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        useItem(s, player);
        helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "the empty bucket should come back full");
        helper.assertValueEqual(s.tunnel().buffer().fluidContents().getAmount(), 500, "left in the buffer");
        useItem(s, player);
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "the full bucket should empty into the buffer");
        helper.assertValueEqual(s.tunnel().buffer().fluidContents().getAmount(), 1500, "back in the buffer");
        helper.succeed();
    }

    /** BT-8: energy is never buffered: a buffered face still passes energy through (empty here, nothing behind). */
    static void energyPassesThrough(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, s.machine(), Direction.NORTH) == EmptyEnergyStorage.INSTANCE,
                "energy on a buffered face should be the pass-through answer");
        helper.succeed();
    }

    /** BT-9: simulated calls on either end change nothing. */
    static void simulateChangesNothing(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        IItemHandler face = faceItems(helper, s, Direction.NORTH);
        helper.assertValueEqual(ItemHandlerHelper.insertItem(face, new ItemStack(Items.COBBLESTONE, 10), true).getCount(), 0, "simulated insert accepted");
        helper.assertValueEqual(buffered(s), 0, "a simulated insert must not fill the buffer");
        TestRooms.insert(face, new ItemStack(Items.COBBLESTONE, 10));
        helper.assertValueEqual(wallItems(s).extractItem(0, 64, true).getCount(), 10, "simulated extract offers");
        helper.assertValueEqual(buffered(s), 10, "a simulated extract must not drain the buffer");
        helper.succeed();
    }

    /** BT-10: the repair pass on every room entry keeps a buffered tunnel's mode and contents. */
    static void repairKeepsBuffer(GameTestHelper helper) {
        Setup s = setup(helper, Direction.NORTH, Direction.EAST, TunnelMode.BUFFERED_IN);
        TestRooms.insert(faceItems(helper, s, Direction.NORTH), new ItemStack(Items.COBBLESTONE, 10));
        RoomBuilder.build(s.rooms(), RoomData.get(helper.getLevel().getServer()).room(s.room().id()).orElseThrow());
        TunnelBlockEntity after = (TunnelBlockEntity) s.rooms().getBlockEntity(s.wall());
        helper.assertValueEqual(after.mode(), TunnelMode.BUFFERED_IN, "mode after repair");
        helper.assertValueEqual(after.buffer().contents().stream().mapToInt(ItemStack::getCount).sum(), 10, "contents after repair");
        helper.succeed();
    }

    private static void click(Setup s, Player player) {
        s.rooms().getBlockState(s.wall()).useWithoutItem(s.rooms(), player, new BlockHitResult(Vec3.atCenterOf(s.wall()), s.inward(), s.wall(), false));
    }

    private static void useItem(Setup s, Player player) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(s.wall()), s.inward(), s.wall(), false);
        s.rooms().getBlockState(s.wall()).useItemOn(player.getMainHandItem(), s.rooms(), player, InteractionHand.MAIN_HAND, hit);
    }

    /** Sneak + right-click with the Tunnel Wrench through the real click event. */
    private static void wrenchClick(Player player, BlockPos wall) {
        ItemStack held = player.getMainHandItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TUNNEL_WRENCH.get()));
        player.setShiftKeyDown(true);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, wall,
                new BlockHitResult(Vec3.atCenterOf(wall), Direction.UP, wall, false)));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
    }

    private BufferedTunnelGameTests() {}
}
