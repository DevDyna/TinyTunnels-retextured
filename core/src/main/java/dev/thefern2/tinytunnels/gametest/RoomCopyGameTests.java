package dev.thefern2.tinytunnels.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.machine.RoomCopy;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
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
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/** Copying a room into an empty machine (room duplicator D0). */
final class RoomCopyGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("0b7c2f4e-3a9d-4f61-8e2a-6c1d5b9f7a20"), "tinytunnels-copy-test");
    private static final BlockState STAIRS = Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);

    /**
     * Survival: the copy has the same blocks in the same states, an empty chest and the same tunnel, and costs exactly
     * the blocks and the tunnel item. The original keeps its chest's items. The held empty machine (2 of them) gives
     * one bound to the new room.
     */
    static void survival(GameTestHelper helper) {
        Room source = furnished(helper);
        ServerPlayer player = player(helper, GameType.SURVIVAL, 2);
        player.getInventory().add(new ItemStack(Items.STONE, 4));
        player.getInventory().add(new ItemStack(Items.OAK_STAIRS));
        player.getInventory().add(new ItemStack(Items.CHEST));
        player.getInventory().add(new ItemStack(ModItems.TUNNEL.get()));

        helper.assertTrue(copy(helper, player), "the copy should succeed");

        Room copy = copiedRoom(helper, player, source);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos floor = new BlockPos(1, 1, 1);
        TestRooms.assertEquals(helper, rooms.getBlockState(at(copy, floor)), Blocks.STONE.defaultBlockState(), "stone in the copy");
        TestRooms.assertEquals(helper, rooms.getBlockState(at(copy, floor.above())), STAIRS, "stairs in the copy");
        helper.assertTrue(rooms.getBlockEntity(at(copy, floor.east())) instanceof ChestBlockEntity chest && chest.isEmpty(), "an empty chest in the copy");
        helper.assertTrue(((ChestBlockEntity) rooms.getBlockEntity(at(source, floor.east()))).getItem(0).is(Items.DIAMOND), "the original keeps its diamonds");
        TestRooms.assertEquals(helper, copy.faceAt(TestRooms.wallCenter(copy.geometry(), Direction.NORTH)), Direction.NORTH, "the copied tunnel's side");

        TestRooms.assertEquals(helper, count(player, Items.STONE), 3, "stone left");
        TestRooms.assertEquals(helper, count(player, Items.OAK_STAIRS), 0, "stairs left");
        TestRooms.assertEquals(helper, count(player, Items.CHEST), 0, "chests left");
        TestRooms.assertEquals(helper, count(player, ModItems.TUNNEL.get()), 0, "tunnels left");
        TestRooms.assertEquals(helper, player.getMainHandItem().getCount(), 1, "empty machines left in hand");
        helper.succeed();
    }

    /** Survival without everything: nothing is taken and no room is made. */
    static void missing(GameTestHelper helper) {
        furnished(helper);
        ServerPlayer player = player(helper, GameType.SURVIVAL, 1);
        player.getInventory().add(new ItemStack(Items.STONE));
        int rooms = RoomData.get(helper.getLevel().getServer()).rooms().size();

        helper.assertTrue(!copy(helper, player), "the copy should be refused");
        TestRooms.assertEquals(helper, count(player, Items.STONE), 1, "stone kept");
        TestRooms.assertEquals(helper, RoomData.get(helper.getLevel().getServer()).rooms().size(), rooms, "rooms");
        helper.assertTrue(!player.getMainHandItem().has(ModDataComponents.ROOM_ID.get()), "the held machine stays empty");
        helper.succeed();
    }

    /** Survival: a spawner can't be paid for, so the copy is refused even with everything else. */
    static void spawnerRefused(GameTestHelper helper) {
        Room source = furnished(helper);
        TestRooms.rooms(helper).setBlock(at(source, new BlockPos(3, 1, 3)), Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = player(helper, GameType.SURVIVAL, 1);
        player.getInventory().add(new ItemStack(Items.STONE));
        player.getInventory().add(new ItemStack(Items.OAK_STAIRS));
        player.getInventory().add(new ItemStack(Items.CHEST));
        player.getInventory().add(new ItemStack(ModItems.TUNNEL.get()));

        helper.assertTrue(!copy(helper, player), "the copy should be refused");
        TestRooms.assertEquals(helper, count(player, Items.STONE), 1, "stone kept");
        helper.succeed();
    }

    /** Creative: free, and everything is copied, a spawner too. */
    static void creative(GameTestHelper helper) {
        Room source = furnished(helper);
        TestRooms.rooms(helper).setBlock(at(source, new BlockPos(3, 1, 3)), Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_ALL);
        ServerPlayer player = player(helper, GameType.CREATIVE, 1);

        helper.assertTrue(copy(helper, player), "the copy should succeed");
        Room copy = copiedRoom(helper, player, source);
        helper.assertTrue(TestRooms.rooms(helper).getBlockState(at(copy, new BlockPos(3, 1, 3))).is(Blocks.SPAWNER), "spawner in the copy");
        helper.succeed();
    }

    /** An empty machine of another size can't take the copy. */
    static void differentSize(GameTestHelper helper) {
        furnished(helper);
        ServerPlayer player = player(helper, GameType.CREATIVE, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MACHINES.get(MachineSize.SMALL).get()));
        helper.assertTrue(!copy(helper, player), "the copy should be refused");
        helper.succeed();
    }

    /** A normal machine whose room has stone, east-facing stairs on it, a chest with diamonds, and a tunnel on N. */
    private static Room furnished(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos floor = new BlockPos(1, 1, 1);
        rooms.setBlock(at(room, floor), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        rooms.setBlock(at(room, floor.above()), STAIRS, Block.UPDATE_ALL);
        rooms.setBlock(at(room, floor.east()), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        ((ChestBlockEntity) rooms.getBlockEntity(at(room, floor.east()))).setItem(0, new ItemStack(Items.DIAMOND, 5));
        TestRooms.addTunnel(helper, room, Direction.NORTH, TestRooms.wallCenter(room.geometry(), Direction.NORTH));
        return RoomData.get(helper.getLevel().getServer()).room(room.id()).orElseThrow();
    }

    private static boolean copy(GameTestHelper helper, ServerPlayer player) {
        return RoomCopy.copy(helper.getLevel(), helper.absolutePos(MACHINE), player, InteractionHand.MAIN_HAND);
    }

    /** The room of the bound machine the copy gave the player. */
    private static Room copiedRoom(GameTestHelper helper, ServerPlayer player, Room source) {
        for (ItemStack stack : player.getInventory().items) {
            UUID id = stack.get(ModDataComponents.ROOM_ID.get());
            if (id != null && !id.equals(source.id())) return RoomData.get(helper.getLevel().getServer()).room(id).orElseThrow();
        }
        throw new AssertionError("no machine bound to a new room in the inventory");
    }

    private static BlockPos at(Room room, BlockPos offset) {
        return room.geometry().min().offset(offset);
    }

    /** A fake player with only {@code machines} empty normal machines in the main hand. */
    private static ServerPlayer player(GameTestHelper helper, GameType mode, int machines) {
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), PROFILE);
        player.setGameMode(mode);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MACHINES.get(MachineSize.NORMAL).get(), machines));
        return player;
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private RoomCopyGameTests() {}
}
