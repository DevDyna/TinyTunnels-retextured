package dev.thefern2.tinytunnels.create.gametest;

import com.mojang.serialization.DynamicOps;

import dev.thefern2.tinytunnels.create.kinetic.KineticMode;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/**
 * The kinetic tunnel when the addon is gone (A5 for the addon's kind). A room with a {@code tinytunnels_create:kinetic}
 * tunnel is saved, its kind id swapped for one that isn't registered (as if the addon were removed), and loaded
 * again; then loaded with the real id, as when the addon comes back. Uses core's room codec and data directly.
 */
final class KineticUnknownGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 2, 2);
    private static final ResourceLocation MISSING = ResourceLocation.fromNamespaceAndPath("tinytunnels_create", "kinetic_missing");

    static void kept(GameTestHelper helper) {
        CreateTestRooms.placeMachine(helper, MACHINE);
        BlockPos wall = CreateTestRooms.addKineticTunnel(helper, MACHINE, Direction.EAST, Direction.EAST, KineticMode.IN);
        ServerLevel rooms = CreateTestRooms.rooms(helper);
        RoomData data = RoomData.get(helper.getLevel().getServer());
        Room room = data.room(CreateTestRooms.room(helper, MACHINE).id()).orElseThrow();
        CompoundTag saved = swapKind(encode(helper, room), MISSING);

        // Without the addon: kept as it was saved, the face used, the inert wall in place.
        Room unknown = decode(helper, saved);
        CreateTestRooms.assertEquals(helper, unknown.unknownKind(Direction.EAST), MISSING, "kept as an unknown kind");
        helper.assertTrue(unknown.isFaceUsed(Direction.EAST), "the face still counts as used");
        CreateTestRooms.assertEquals(helper, encode(helper, unknown), saved, "saved back unchanged");
        data.replace(unknown);
        RoomBuilder.build(rooms, unknown);
        helper.assertTrue(rooms.getBlockState(wall).is(dev.thefern2.tinytunnels.registry.ModBlocks.UNKNOWN_TUNNEL_WALL.get()),
                "the unknown wall should hold the place, got " + rooms.getBlockState(wall));

        // The addon back: the kinetic tunnel returns, IN, with its wall.
        Room back = decode(helper, swapKind(saved, ModTunnelKinds.KINETIC.getId()));
        CreateTestRooms.assertEquals(helper, back.data(Direction.EAST, ModTunnelKinds.KINETIC.get()), KineticMode.IN, "the kinetic tunnel's data is back");
        data.replace(back);
        RoomBuilder.build(rooms, back);
        helper.assertTrue(rooms.getBlockState(wall).is(ModBlocks.KINETIC_TUNNEL_WALL.get()),
                "the kinetic wall should be rebuilt, got " + rooms.getBlockState(wall));
        helper.succeed();
    }

    private static CompoundTag encode(GameTestHelper helper, Room room) {
        return (CompoundTag) Room.CODEC.encodeStart(ops(helper), room).getOrThrow();
    }

    private static Room decode(GameTestHelper helper, CompoundTag tag) {
        return Room.CODEC.parse(ops(helper), tag.copy()).getOrThrow();
    }

    private static DynamicOps<Tag> ops(GameTestHelper helper) {
        return helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static CompoundTag swapKind(CompoundTag room, ResourceLocation kind) {
        CompoundTag copy = room.copy();
        copy.getCompound("tunnels").getCompound(Direction.EAST.getSerializedName()).putString("kind", kind.toString());
        return copy;
    }

    private KineticUnknownGameTests() {}
}
