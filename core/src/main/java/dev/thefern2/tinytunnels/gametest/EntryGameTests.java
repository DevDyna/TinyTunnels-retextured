package dev.thefern2.tinytunnels.gametest;

import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.room.EntryPoint;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomGeometry;
import dev.thefern2.tinytunnels.teleport.RoomEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Where players appear when entering a room that has been built in. */
final class EntryGameTests {
    private static final EntityDimensions PLAYER = EntityType.PLAYER.getDimensions();

    /** The spot a player left from is where the next entry starts, facing the same way. */
    static void savedExitIsUsed(GameTestHelper helper) {
        Room room = newRoom(helper);
        Vec3 spot = room.geometry().min().offset(2, 1, 2).getBottomCenter();
        RoomEntry.rememberExit(TestRooms.rooms(helper), spot, 90f, 10f);
        EntryPoint entry = RoomEntry.find(TestRooms.rooms(helper), reload(helper, room), PLAYER, 0f, 0f);
        helper.assertValueEqual(entry.pos(), spot, "entry position");
        helper.assertValueEqual(entry.yRot(), 90f, "entry facing");
        helper.succeed();
    }

    /** A saved spot that has since been built over is skipped for the floor centre. */
    static void blockedSavedExitFallsBack(GameTestHelper helper) {
        Room room = newRoom(helper);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos spot = room.geometry().min().offset(2, 1, 2);
        RoomEntry.rememberExit(rooms, spot.getBottomCenter(), 90f, 10f);
        rooms.setBlock(spot.above(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        EntryPoint entry = RoomEntry.find(rooms, reload(helper, room), PLAYER, 45f, 0f);
        helper.assertValueEqual(entry.pos(), room.geometry().spawn(), "entry position");
        helper.assertValueEqual(entry.yRot(), 45f, "the player's own facing");
        helper.succeed();
    }

    /** With the floor centre built over too, entry is the nearest clear spot, standing on something. */
    static void blockedCentreFindsClearSpot(GameTestHelper helper) {
        Room room = newRoom(helper);
        ServerLevel rooms = TestRooms.rooms(helper);
        RoomGeometry geometry = room.geometry();
        // Fill the whole floor layer except one block, so the only clear standing spot is there.
        BlockPos free = geometry.min().offset(1, 1, 1);
        for (BlockPos pos : BlockPos.betweenClosed(geometry.min().offset(1, 1, 1), geometry.max().offset(-1, 0, -1).atY(geometry.min().getY() + 1))) {
            if (!pos.equals(free)) rooms.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        EntryPoint entry = RoomEntry.find(rooms, reload(helper, room), PLAYER, 0f, 0f);
        helper.assertTrue(RoomEntry.fits(rooms, room, entry.pos(), PLAYER), "the entry spot should be clear");
        helper.assertTrue(BlockPos.containing(entry.pos()).getY() == geometry.min().getY() + 2 || BlockPos.containing(entry.pos()).equals(free),
                "should stand on the stone or in the one free floor block, was " + entry.pos());
        helper.succeed();
    }

    /** Leaving from outside any room's interior doesn't change the entry point. */
    static void exitOutsideRoomIgnored(GameTestHelper helper) {
        Room room = newRoom(helper);
        RoomEntry.rememberExit(TestRooms.rooms(helper), room.geometry().min().getBottomCenter(), 0f, 0f);
        helper.assertTrue(reload(helper, room).entry().isEmpty(), "a wall position must not become the entry point");
        helper.succeed();
    }

    /** With the default config, using a bed inside a room is refused instead of exploding. */
    static void bedRefusedInRoom(GameTestHelper helper) {
        Room room = newRoom(helper);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos bed = room.geometry().min().offset(2, 1, 2);
        rooms.setBlock(bed, Blocks.RED_BED.defaultBlockState(), Block.UPDATE_ALL);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var event = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, bed,
                new BlockHitResult(Vec3.atCenterOf(bed), Direction.UP, bed, false)));
        helper.assertValueEqual(event.getUseBlock(), TriState.FALSE, "bed use inside a room");
        helper.assertTrue(rooms.getBlockState(bed).is(BlockTags.BEDS), "the bed should still be there");
        helper.succeed();
    }

    private static Room newRoom(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, TunnelGameTests.MACHINE);
        return TestRooms.room(helper, machine);
    }

    private static Room reload(GameTestHelper helper, Room room) {
        return RoomData.get(helper.getLevel().getServer()).room(room.id()).orElseThrow();
    }

    private EntryGameTests() {}
}
