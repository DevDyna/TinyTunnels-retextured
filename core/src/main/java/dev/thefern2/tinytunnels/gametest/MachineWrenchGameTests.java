package dev.thefern2.tinytunnels.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.thefern2.tinytunnels.Config;
import dev.thefern2.tinytunnels.machine.MachineFaces;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineItem;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.machine.MachineWrenching;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;

/** The Tunnel Wrench on a machine block (machine management M2 pick-up, M3 delete, M5 turn). */
final class MachineWrenchGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("5f1d3e0a-7b7c-4c1e-9a55-2f0c9d7e4b11"), "tinytunnels-wrench-test");

    /** M2: breaking a machine with the wrench in creative gives the machine item bound to its room. */
    static void creativePickUp(GameTestHelper helper) {
        UUID room = TestRooms.placeMachine(helper, MACHINE).getRoomId();
        ServerPlayer player = creativePlayer(helper, new ItemStack(ModItems.TUNNEL_WRENCH.get()));
        player.gameMode.destroyBlock(helper.absolutePos(MACHINE));
        helper.assertTrue(!(helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE)) instanceof MachineHost), "machine should be gone");
        TestRooms.assertEquals(helper, boundItems(player, room), 1, "machine items bound to the room");
        helper.assertTrue(RoomData.get(helper.getLevel().getServer()).room(room).orElseThrow().host().isEmpty(),
                "the room should have no host after pick-up");
        helper.succeed();
    }

    /** M2: without the wrench, a creative break stays vanilla: no item. */
    static void creativeBreakWithoutWrench(GameTestHelper helper) {
        UUID room = TestRooms.placeMachine(helper, MACHINE).getRoomId();
        ServerPlayer player = creativePlayer(helper, ItemStack.EMPTY);
        player.gameMode.destroyBlock(helper.absolutePos(MACHINE));
        helper.assertTrue(!(helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE)) instanceof MachineHost), "machine should be gone");
        TestRooms.assertEquals(helper, boundItems(player, room), 0, "machine items bound to the room");
        helper.succeed();
    }

    /**
     * M3: sneak + wrench twice deletes the room. The first click only asks. The contents come back: blocks, a
     * chest's items, the tunnel, an unbound machine. The room is gone from the data and the dimension, and its grid
     * slot is reused.
     */
    static void deleteKeepsContents(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos floor = room.geometry().min().offset(1, 1, 1);
        rooms.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        rooms.setBlock(floor.above(), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        ((ChestBlockEntity) rooms.getBlockEntity(floor.above())).setItem(0, new ItemStack(Items.DIAMOND, 5));
        TestRooms.addTunnel(helper, room, Direction.NORTH, TestRooms.wallCenter(room.geometry(), Direction.NORTH));
        whenLoaded(helper, rooms, room, () -> deleteAndKeep(helper, rooms, room));
    }

    private static void deleteAndKeep(GameTestHelper helper, ServerLevel rooms, Room room) {
        ServerPlayer player = player(helper, GameType.SURVIVAL, new ItemStack(ModItems.TUNNEL_WRENCH.get()));
        MachineWrenching.deleteClick(helper.getLevel(), helper.absolutePos(MACHINE), player);
        helper.assertTrue(data(helper).room(room.id()).isPresent(), "one click shouldn't delete the room");
        MachineWrenching.deleteClick(helper.getLevel(), helper.absolutePos(MACHINE), player);

        helper.assertTrue(data(helper).room(room.id()).isEmpty(), "the room should be deleted");
        helper.assertTrue(!(helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE)) instanceof MachineHost), "machine should be gone");
        assertCleared(helper, rooms, room);
        TestRooms.assertEquals(helper, count(player, Items.STONE), 1, "stone back");
        TestRooms.assertEquals(helper, count(player, Items.CHEST), 1, "chest back");
        TestRooms.assertEquals(helper, count(player, Items.DIAMOND), 5, "chest contents back");
        TestRooms.assertEquals(helper, count(player, ModItems.TUNNEL.get()), 1, "tunnel back");
        TestRooms.assertEquals(helper, unboundMachines(player), 1, "unbound machine back");

        Room reused = data(helper).allocate(MachineSize.NORMAL.getInterior());
        helper.assertTrue(reused.gridIndex() <= room.gridIndex(), "a new room should reuse a freed slot, got "
                + reused.gridIndex() + " after freeing " + room.gridIndex());
        data(helper).delete(reused.id());
        helper.succeed();
    }

    /**
     * M3 with {@code deletingRoomDestroysContents} on: everything inside is destroyed, a nested machine's room too;
     * only the unbound machine comes back.
     */
    static void deleteDestroysContents(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos floor = room.geometry().min().offset(1, 1, 1);
        rooms.setBlock(floor, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        Room nested = TestRooms.room(helper, TestRooms.placeMachine(rooms, floor.above(), MachineSize.NORMAL));
        whenLoaded(helper, rooms, room, () -> whenLoaded(helper, rooms, nested, () -> deleteAndDestroy(helper, rooms, room, nested)));
    }

    private static void deleteAndDestroy(GameTestHelper helper, ServerLevel rooms, Room room, Room nested) {
        ServerPlayer player = player(helper, GameType.SURVIVAL, new ItemStack(ModItems.TUNNEL_WRENCH.get()));
        Config.DELETING_ROOM_DESTROYS_CONTENTS.set(true);
        try {
            MachineWrenching.deleteClick(helper.getLevel(), helper.absolutePos(MACHINE), player);
            MachineWrenching.deleteClick(helper.getLevel(), helper.absolutePos(MACHINE), player);
        } finally {
            Config.DELETING_ROOM_DESTROYS_CONTENTS.set(false);
        }

        helper.assertTrue(data(helper).room(room.id()).isEmpty(), "the room should be deleted");
        helper.assertTrue(data(helper).room(nested.id()).isEmpty(), "the nested room should be deleted too");
        assertCleared(helper, rooms, room);
        assertCleared(helper, rooms, nested);
        TestRooms.assertEquals(helper, count(player, Items.STONE), 0, "stone destroyed");
        TestRooms.assertEquals(helper, unboundMachines(player), 1, "unbound machine back");
        TestRooms.assertEquals(helper, nonEmptyStacks(player), 2, "stacks in the inventory (wrench and machine)");
        helper.succeed();
    }

    /**
     * M5: right-click with the wrench turns the machine clockwise. The north tunnel links to the east side (its wall
     * says so, items go through it from the east), the top one stays, and the face letters follow. Four turns
     * bring it back.
     */
    static void turn(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos northWall = TestRooms.wallCenter(room.geometry(), Direction.NORTH);
        BlockPos upWall = TestRooms.wallCenter(room.geometry(), Direction.UP);
        BlockPos inside = TestRooms.addTunnel(helper, room, Direction.NORTH, northWall);
        TestRooms.addTunnel(helper, room, Direction.UP, upWall);
        rooms.setBlock(inside, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);

        ServerPlayer player = player(helper, GameType.SURVIVAL, new ItemStack(ModItems.TUNNEL_WRENCH.get()));
        MachineWrenching.turn(helper.getLevel(), helper.absolutePos(MACHINE), player);

        Room turned = data(helper).room(room.id()).orElseThrow();
        TestRooms.assertEquals(helper, turned.faceAt(northWall), Direction.EAST, "side the north wall's tunnel links to");
        TestRooms.assertEquals(helper, turned.faceAt(upWall), Direction.UP, "side the top tunnel links to");
        helper.assertTrue(turned.at(Direction.NORTH) == null, "nothing should link to the north side now");
        TestRooms.assertEquals(helper, rooms.getBlockState(northWall).getValue(TunnelWallBlock.FACE), Direction.EAST, "north wall's face");
        IItemHandler east = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MACHINE), Direction.EAST);
        helper.assertTrue(east != null, "no item handler on the east side");
        TestRooms.assertEquals(helper, TestRooms.insert(east, new ItemStack(Items.COBBLESTONE)), 1, "inserted through the east side");
        TestRooms.assertEquals(helper, ((ChestBlockEntity) rooms.getBlockEntity(inside)).getItem(0).getCount(), 1, "items in the chest inside");

        helper.succeedWhen(() -> {
            MachineFaces faces = TestRooms.faces(helper, MACHINE);
            helper.assertTrue(faces.has(Direction.EAST, MachineFaces.TRANSFER), "east letter after the turn: " + faces);
            helper.assertTrue(!faces.has(Direction.NORTH, MachineFaces.TRANSFER), "north letter gone after the turn: " + faces);
            helper.assertTrue(faces.has(Direction.UP, MachineFaces.TRANSFER), "top letter stays: " + faces);
            for (int i = 0; i < 3; i++) MachineWrenching.turn(helper.getLevel(), helper.absolutePos(MACHINE), player);
            TestRooms.assertEquals(helper, data(helper).room(room.id()).orElseThrow().faceAt(northWall), Direction.NORTH,
                    "side after four turns");
        });
    }

    /** Runs {@code then} once the room's entities are loaded (the room gets its ticket on the next server tick). */
    private static void whenLoaded(GameTestHelper helper, ServerLevel rooms, Room room, Runnable then) {
        if (rooms.areEntitiesLoaded(room.geometry().chunk().toLong())) {
            then.run();
        } else {
            helper.runAfterDelay(1, () -> whenLoaded(helper, rooms, room, then));
        }
    }

    private static RoomData data(GameTestHelper helper) {
        return RoomData.get(helper.getLevel().getServer());
    }

    private static void assertCleared(GameTestHelper helper, ServerLevel rooms, Room room) {
        for (BlockPos pos : BlockPos.betweenClosed(room.geometry().min(), room.geometry().max())) {
            helper.assertTrue(rooms.getBlockState(pos).isAir(), "room block left at " + pos.toShortString() + ": " + rooms.getBlockState(pos));
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static int unboundMachines(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof MachineItem && !stack.has(ModDataComponents.ROOM_ID.get())) count += stack.getCount();
        }
        return count;
    }

    private static int nonEmptyStacks(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty()) count++;
        }
        return count;
    }

    private static ServerPlayer creativePlayer(GameTestHelper helper, ItemStack mainHand) {
        return player(helper, GameType.CREATIVE, mainHand);
    }

    /**
     * A fake player: {@code destroyBlock} and the wrench need a server player, and the GameTest mock server player joins
     * the player list, where other mods (Jade) send it packets its fake connection can't take.
     */
    private static ServerPlayer player(GameTestHelper helper, GameType mode, ItemStack mainHand) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), PROFILE);
        player.setGameMode(mode);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, mainHand);
        return player;
    }

    private static int boundItems(ServerPlayer player, UUID room) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (room.equals(stack.get(ModDataComponents.ROOM_ID.get()))) count += stack.getCount();
        }
        return count;
    }

    private MachineWrenchGameTests() {}
}
