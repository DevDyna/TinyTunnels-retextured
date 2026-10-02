package dev.thefern2.tinytunnels.gametest;

import java.util.EnumMap;
import java.util.Map;

import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.machine.MachineFaces;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.tunnel.RedstoneSignal;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Redstone tunnels: signals in and out on every face, analog strength, direction changes, loops,
 * removal, the wrench and nesting. The machine stands at {@link TunnelGameTests#MACHINE}. Waits are
 * generous because a room's scheduled ticks only run once its chunk is ticking.
 */
final class RedstoneGameTests {
    private static final BlockPos MACHINE = TunnelGameTests.MACHINE;

    /** Every machine face carries a signal in, each through a different wall. */
    static void inFaceMatrix(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        Map<Direction, BlockPos> lamps = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            BlockPos inside = TestRooms.addRedstoneTunnel(helper, room, face, TestRooms.wallCenter(room.geometry(), wallFor(face)), RedstoneMode.IN);
            rooms.setBlock(inside, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
            lamps.put(face, inside);
            helper.getLevel().setBlock(machinePos.relative(face), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
        helper.succeedWhen(() -> lamps.forEach((face, lamp) -> {
            helper.assertValueEqual(TestRooms.redstonePower(helper, room, face), 15, "power in through " + face.getSerializedName());
            helper.assertTrue(lit(rooms, lamp), "lamp inside, face " + face.getSerializedName());
            helper.assertValueEqual(look(helper, machinePos, face), MachineFaces.REDSTONE_LIT_LOOK, "machine port look, face " + face.getSerializedName());
        }));
    }

    /** Every machine face carries a signal out, each through a different wall. */
    static void outFaceMatrix(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        for (Direction face : Direction.values()) {
            BlockPos inside = TestRooms.addRedstoneTunnel(helper, room, face, TestRooms.wallCenter(room.geometry(), wallFor(face)), RedstoneMode.OUT);
            rooms.setBlock(inside, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
            helper.getLevel().setBlock(machinePos.relative(face), Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        }
        helper.succeedWhen(() -> {
            for (Direction face : Direction.values()) {
                helper.assertValueEqual(TestRooms.redstonePower(helper, room, face), 15, "power out through " + face.getSerializedName());
                helper.assertTrue(lit(helper.getLevel(), machinePos.relative(face)), "lamp outside, face " + face.getSerializedName());
                helper.assertValueEqual(look(helper, machinePos, face), MachineFaces.REDSTONE_LIT_LOOK, "machine port look, face " + face.getSerializedName());
            }
        });
    }

    /** Dust three blocks from its source (13) comes out of the machine at 13. */
    static void analog(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = lowWestWall(room, 0);
        BlockPos inside = TestRooms.addRedstoneTunnel(helper, room, Direction.NORTH, wall, RedstoneMode.OUT);
        for (int i = 0; i < 3; i++) placeDust(rooms, inside.east(i));
        rooms.setBlock(inside.east(3), Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(TestRooms.redstonePower(helper, room, Direction.NORTH), 13, "stored power");
            helper.assertValueEqual(helper.getLevel().getSignal(machinePos, Direction.SOUTH), 13, "signal the machine gives its north neighbour");
        });
    }

    /** Right-click with an empty hand flips the direction; the old output goes dark and the new one works. */
    static void toggleMode(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = TestRooms.wallCenter(room.geometry(), Direction.WEST);
        BlockPos inside = TestRooms.addRedstoneTunnel(helper, room, Direction.EAST, wall, RedstoneMode.IN);
        BlockPos outside = MACHINE.east();
        rooms.setBlock(inside, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        helper.setBlock(outside, Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lit(rooms, inside), "waiting for the lamp inside"))
                .thenExecute(() -> click(helper, wall, helper.makeMockPlayer(GameType.SURVIVAL)))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(mode(helper, room, Direction.EAST), RedstoneMode.OUT, "mode after the click");
                    helper.assertFalse(lit(rooms, inside), "waiting for the lamp inside to go off");
                    helper.assertValueEqual(look(helper, helper.absolutePos(MACHINE), Direction.EAST), MachineFaces.REDSTONE_LOOK, "machine port look after the flip");
                })
                .thenExecute(() -> {
                    rooms.setBlock(inside, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    helper.setBlock(outside, Blocks.REDSTONE_LAMP);
                })
                .thenWaitUntil(() -> helper.assertTrue(lit(helper.getLevel(), helper.absolutePos(outside)), "waiting for the lamp outside"))
                .thenSucceed();
    }

    /**
     * An IN face and an OUT face wired to each other inside and outside. A kick starts the signal
     * going round; once the kick is gone it fades out instead of holding itself on, and nothing recurses.
     */
    static void loop(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos in = TestRooms.addRedstoneTunnel(helper, room, Direction.NORTH, lowWestWall(room, 0), RedstoneMode.IN);
        BlockPos out = TestRooms.addRedstoneTunnel(helper, room, Direction.SOUTH, lowWestWall(room, 1), RedstoneMode.OUT);
        placeDust(rooms, in);
        placeDust(rooms, out);
        // Outside: dust from the south face round the west side to the north face, on stone.
        BlockPos[] ring = {MACHINE.south(), MACHINE.south().west(), MACHINE.west(), MACHINE.north().west(), MACHINE.north()};
        for (BlockPos pos : ring) {
            helper.setBlock(pos.below(), Blocks.STONE);
            placeDust(helper.getLevel(), helper.absolutePos(pos));
        }
        BlockPos kick = MACHINE.north().east();
        helper.setBlock(kick, Blocks.REDSTONE_BLOCK);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(TestRooms.redstonePower(helper, room, Direction.SOUTH) > 0, "waiting for the signal to come round"))
                .thenExecute(() -> helper.setBlock(kick, Blocks.AIR))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(TestRooms.redstonePower(helper, room, Direction.NORTH), 0, "power in after the kick");
                    helper.assertValueEqual(TestRooms.redstonePower(helper, room, Direction.SOUTH), 0, "power out after the kick");
                })
                .thenSucceed();
    }

    /** A redstone face moves no items from either side; an item tunnel on another face still does. */
    static void noCapabilities(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        BlockPos redstoneWall = TestRooms.wallCenter(room.geometry(), Direction.NORTH);
        TestRooms.addRedstoneTunnel(helper, room, Direction.UP, redstoneWall, RedstoneMode.IN);
        BlockPos chest = TestRooms.addTunnel(helper, room, Direction.DOWN, TestRooms.wallCenter(room.geometry(), Direction.SOUTH));
        rooms.setBlock(chest, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

        helper.assertTrue(helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machinePos, Direction.UP) == null,
                "the machine's redstone face should expose no items");
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, machinePos, Direction.UP) == null,
                "the machine's redstone face should expose no energy");
        helper.assertTrue(rooms.getCapability(Capabilities.ItemHandler.BLOCK, redstoneWall, Direction.SOUTH) == null,
                "the redstone tunnel should expose no items inside");
        var items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machinePos, Direction.DOWN);
        helper.assertTrue(items != null, "the item tunnel face should still answer");
        helper.assertValueEqual(TestRooms.insert(items, new ItemStack(Items.COBBLESTONE)), 1, "inserted through the item tunnel");
        helper.succeed();
    }

    /** Picking up the machine darkens the inside. */
    static void machineRemoved(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = TestRooms.wallCenter(room.geometry(), Direction.EAST);
        BlockPos inside = TestRooms.addRedstoneTunnel(helper, room, Direction.WEST, wall, RedstoneMode.IN);
        rooms.setBlock(inside, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        helper.setBlock(MACHINE.west(), Blocks.REDSTONE_BLOCK);
        // Without its machine the room stops ticking, so the lamp's own switch-off delay never runs;
        // check what the lamp is being sent instead.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lit(rooms, inside), "waiting for the lamp inside"))
                .thenExecute(() -> helper.setBlock(MACHINE, Blocks.AIR))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(TestRooms.redstonePower(helper, room, Direction.WEST), 0, "stored power");
                    helper.assertValueEqual(rooms.getSignal(wall, Direction.EAST), 0, "signal the wall gives the lamp");
                })
                .thenSucceed();
    }

    /** The wrench moves the output to the next free face, then removes the tunnel. */
    static void wrench(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = TestRooms.wallCenter(room.geometry(), Direction.WEST);
        BlockPos inside = TestRooms.addRedstoneTunnel(helper, room, Direction.NORTH, wall, RedstoneMode.OUT);
        rooms.setBlock(inside, Blocks.REDSTONE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        helper.setBlock(MACHINE.north(), Blocks.REDSTONE_LAMP);
        // After north, the next free face in Direction order is south.
        helper.setBlock(MACHINE.south(), Blocks.REDSTONE_LAMP);
        BlockPos north = helper.absolutePos(MACHINE.north());
        BlockPos south = helper.absolutePos(MACHINE.south());
        BlockPos machinePos = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(lit(helper.getLevel(), north), "waiting for the north lamp"))
                .thenExecute(() -> wrenchClick(helper, wall, false))
                .thenWaitUntil(() -> {
                    helper.assertFalse(lit(helper.getLevel(), north), "waiting for the north lamp to go off");
                    helper.assertTrue(lit(helper.getLevel(), south), "waiting for the south lamp");
                    helper.assertTrue(TestRooms.faces(helper.getLevel(), machinePos).has(Direction.SOUTH, MachineFaces.REDSTONE), "south port");
                })
                .thenExecute(() -> wrenchClick(helper, wall, true))
                .thenWaitUntil(() -> {
                    helper.assertTrue(rooms.getBlockState(wall).is(ModBlocks.ROOM_WALL.get()), "the wall should be a plain wall again");
                    helper.assertValueEqual(TestRooms.redstonePower(helper, room, Direction.SOUTH), -1, "no tunnel left on south");
                    helper.assertFalse(lit(helper.getLevel(), south), "waiting for the south lamp to go off");
                    helper.assertFalse(TestRooms.faces(helper.getLevel(), machinePos).has(Direction.SOUTH, MachineFaces.REDSTONE), "south port cleared");
                })
                .thenSucceed();
    }

    /** A signal passes into a machine nested inside another machine's room. */
    static void nested(GameTestHelper helper) {
        MachineHost outer = TestRooms.placeMachine(helper, MACHINE);
        Room outerRoom = TestRooms.room(helper, outer);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos innerPos = TestRooms.addRedstoneTunnel(helper, outerRoom, Direction.NORTH, TestRooms.wallCenter(outerRoom.geometry(), Direction.SOUTH), RedstoneMode.IN);
        MachineHost inner = TestRooms.placeMachine(rooms, innerPos, MachineSize.NORMAL);
        Room innerRoom = TestRooms.room(helper, inner);
        BlockPos lamp = TestRooms.addRedstoneTunnel(helper, innerRoom, Direction.SOUTH, TestRooms.wallCenter(innerRoom.geometry(), Direction.SOUTH), RedstoneMode.IN);
        rooms.setBlock(lamp, Blocks.REDSTONE_LAMP.defaultBlockState(), Block.UPDATE_ALL);
        helper.setBlock(MACHINE.north(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertValueEqual(TestRooms.redstonePower(helper, outerRoom, Direction.NORTH), 15, "outer power");
            helper.assertValueEqual(TestRooms.redstonePower(helper, innerRoom, Direction.SOUTH), 15, "inner power");
            helper.assertTrue(lit(rooms, lamp), "lamp in the inner room");
        });
    }

    /** The Redstone Tunnel item, used on a wall, makes an IN tunnel on the first free face and sets its port. */
    static void itemAndPorts(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = TestRooms.wallCenter(room.geometry(), Direction.WEST);
        TestRooms.addTunnel(helper, room, Direction.DOWN, TestRooms.wallCenter(room.geometry(), Direction.EAST));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REDSTONE_TUNNEL.get()));
        click(helper, wall, player);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        // Everything after the click waits: a player is never this fast, and a result may land a tick later.
        helper.succeedWhen(() -> {
            helper.assertTrue(rooms.getBlockState(wall).is(ModBlocks.REDSTONE_TUNNEL_WALL.get()), "the wall should be a redstone tunnel, got " + rooms.getBlockState(wall));
            RedstoneMode mode = mode(helper, room, Direction.UP);
            helper.assertTrue(mode == RedstoneMode.IN, "new tunnel on the first free face (up) should be IN, got " + mode);
            helper.assertTrue(player.getMainHandItem().isEmpty(), "the item should be used up, got " + player.getMainHandItem());
            MachineFaces faces = TestRooms.faces(helper.getLevel(), machinePos);
            helper.assertTrue(faces.has(Direction.UP, MachineFaces.REDSTONE), "up port should be redstone");
            helper.assertFalse(faces.has(Direction.UP, MachineFaces.TRANSFER), "up port is not an item tunnel");
            helper.assertTrue(faces.has(Direction.DOWN, MachineFaces.TRANSFER), "down port should be an item tunnel");
        });
    }

    /** The machine face's look follows the signal: lit while power is above 0, unlit again at 0. */
    static void litLook(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        TestRooms.addRedstoneTunnel(helper, room, Direction.EAST, TestRooms.wallCenter(room.geometry(), Direction.WEST), RedstoneMode.IN);
        BlockPos machinePos = helper.absolutePos(MACHINE);
        helper.startSequence()
                .thenExecute(() -> helper.assertValueEqual(look(helper, machinePos, Direction.EAST), MachineFaces.REDSTONE_LOOK, "unlit look before any signal"))
                .thenExecute(() -> helper.setBlock(MACHINE.east(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(TestRooms.redstonePower(helper, room, Direction.EAST), 15, "power in");
                    helper.assertValueEqual(look(helper, machinePos, Direction.EAST), MachineFaces.REDSTONE_LIT_LOOK, "lit look while powered");
                })
                .thenExecute(() -> helper.setBlock(MACHINE.east(), Blocks.AIR))
                .thenWaitUntil(() -> {
                    helper.assertValueEqual(TestRooms.redstonePower(helper, room, Direction.EAST), 0, "power after the source is gone");
                    helper.assertValueEqual(look(helper, machinePos, Direction.EAST), MachineFaces.REDSTONE_LOOK, "unlit look at 0");
                })
                .thenSucceed();
    }

    /** Entering a room repairs its shell; that must keep redstone tunnels (it used to turn them into plain walls). */
    static void shellRepairKeepsRedstone(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = TestRooms.wallCenter(room.geometry(), Direction.DOWN);
        TestRooms.addRedstoneTunnel(helper, room, Direction.NORTH, wall, RedstoneMode.OUT);
        RoomBuilder.build(rooms, TestRooms.room(helper, machine));
        var state = rooms.getBlockState(wall);
        helper.assertTrue(state.is(ModBlocks.REDSTONE_TUNNEL_WALL.get()), "the redstone tunnel should survive the shell repair");
        helper.assertValueEqual(state.getValue(RedstoneTunnelWallBlock.MODE), RedstoneMode.OUT, "mode after the repair");

        // A redstone tunnel whose wall went missing is put back.
        ShellProtection.edit(() -> rooms.setBlock(wall, ModBlocks.ROOM_WALL.get().defaultBlockState(), Block.UPDATE_ALL));
        RoomBuilder.build(rooms, TestRooms.room(helper, machine));
        helper.assertTrue(rooms.getBlockState(wall).is(ModBlocks.REDSTONE_TUNNEL_WALL.get()), "a missing redstone tunnel should be restored");
        helper.assertValueEqual(rooms.getBlockState(wall).getValue(RedstoneTunnelWallBlock.FACE), Direction.NORTH, "restored face");
        helper.succeed();
    }

    // Helpers

    /** A wall for each face that isn't the face's own side, so a face/side mix-up can't hide. */
    private static Direction wallFor(Direction face) {
        return Direction.values()[(face.ordinal() + 2) % 6];
    }

    /** A west wall block on the bottom row (so the block inside sits on the floor), {@code offset} blocks south of the middle. */
    private static BlockPos lowWestWall(Room room, int offset) {
        BlockPos min = room.geometry().min();
        int middle = 1 + room.geometry().interior() / 2;
        return min.offset(0, 1, middle + offset);
    }

    private static void placeDust(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Block.updateFromNeighbourShapes(Blocks.REDSTONE_WIRE.defaultBlockState(), level, pos), Block.UPDATE_ALL);
    }

    private static boolean lit(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
    }

    /** The look the machine last sent its clients for {@code face}, or null if the face has no tunnel. */
    private static FaceLook look(GameTestHelper helper, BlockPos machinePos, Direction face) {
        return TestRooms.faces(helper.getLevel(), machinePos).lookAt(face);
    }

    private static RedstoneMode mode(GameTestHelper helper, Room room, Direction face) {
        RedstoneSignal signal = RoomData.get(helper.getLevel().getServer()).room(room.id()).orElseThrow().data(face, ModTunnelKinds.REDSTONE.get());
        return signal == null ? null : signal.mode();
    }

    /**
     * Right-clicks a block the way a player does (block first, then the empty-hand use, then the
     * item). {@code GameTestHelper.useBlock} takes relative positions, and its {@code relativePos}
     * gets the rotation wrong for unrotated tests, so room positions can't go through it.
     */
    static void click(GameTestHelper helper, BlockPos pos, Player player) {
        ServerLevel level = helper.getLevel();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        var state = level.getBlockState(pos);
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        ItemInteractionResult result = state.useItemOn(stack, level, player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) return;
        if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && state.useWithoutItem(level, player, hit).consumesAction()) return;
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    /** Right-clicks the wall with the Tunnel Wrench through the real click event, sneaking or not. */
    static void wrenchClick(GameTestHelper helper, BlockPos wall, boolean sneak) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TUNNEL_WRENCH.get()));
        player.setShiftKeyDown(sneak);
        NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, wall,
                new BlockHitResult(Vec3.atCenterOf(wall), Direction.UP, wall, false)));
    }

    private RedstoneGameTests() {}
}
