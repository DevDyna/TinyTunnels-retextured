package dev.thefern2.tinytunnels.gametest;

import com.mojang.serialization.DynamicOps;

import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomBuilder;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Tunnels of a kind that isn't registered (its mod removed), with the GameTest-only {@code tinytunnels:test_kind}
 * standing in for an addon's kind (A5.4). The room is saved, its kind id swapped for one that isn't registered,
 * and loaded again, as when a world is opened without the addon. Create-free; runs with and without Create.
 */
final class UnknownKindGameTests {
    private static final ResourceLocation MISSING = ResourceLocation.fromNamespaceAndPath("tinytunnels", "missing_kind");
    private static final Direction FACE = Direction.DOWN;
    private static final int DATA = 7;
    private static final ResourceLocation CREATE_KINETIC = ResourceLocation.fromNamespaceAndPath("tinytunnels_create", "kinetic");

    /**
     * (a) The unknown entry survives as raw data and saves back the same; its face counts as used; the shell
     * repair puts the inert unknown wall at its place, so there's no hole.
     */
    static void kept(GameTestHelper helper) {
        Setup setup = setup(helper);
        Room unknown = decode(helper, setup.saved);
        helper.assertValueEqual(unknown.unknownKind(FACE), MISSING, "unknown kind id kept");
        helper.assertTrue(unknown.faces().isEmpty(), "no registered tunnel decoded");
        helper.assertTrue(unknown.isFaceUsed(FACE), "the unknown face counts as used");
        helper.assertFalse(unknown.view().tunnels().containsKey(FACE), "RoomView.tunnels() leaves the unknown entry out");
        helper.assertTrue(unknown.view().isFaceUsed(FACE), "RoomView.isFaceUsed counts the unknown entry");
        helper.assertValueEqual(encode(helper, unknown), setup.saved, "saved back unchanged");

        RoomData.get(helper.getLevel().getServer()).replace(unknown);
        RoomBuilder.build(setup.rooms, unknown);
        helper.assertTrue(setup.rooms.getBlockState(setup.wall).is(ModBlocks.UNKNOWN_TUNNEL_WALL.get()),
                "the shell repair should put the unknown wall there, got " + setup.rooms.getBlockState(setup.wall));
        helper.succeed();
    }

    /** (b) Loaded again with the kind registered, the tunnel comes back with its data, and repair rebuilds its wall. */
    static void returns(GameTestHelper helper) {
        Setup setup = setup(helper);
        Room unknown = decode(helper, setup.saved);
        RoomData.get(helper.getLevel().getServer()).replace(unknown);
        RoomBuilder.build(setup.rooms, unknown);

        CompoundTag again = swapKind(encode(helper, unknown), TestTunnelKind.KIND.getId());
        Room back = decode(helper, again);
        helper.assertTrue(back.unknownKind(FACE) == null, "no unknown entry left, got " + back.unknownKind(FACE));
        helper.assertValueEqual(back.data(FACE, TestTunnelKind.KIND.get()), DATA, "the test kind's data is back");
        RoomData.get(helper.getLevel().getServer()).replace(back);
        RoomBuilder.build(setup.rooms, back);
        helper.assertTrue(setup.rooms.getBlockState(setup.wall).is(Blocks.REINFORCED_DEEPSLATE),
                "the shell repair should rebuild the test kind's wall, got " + setup.rooms.getBlockState(setup.wall));
        helper.succeed();
    }

    /** (c) {@link TunnelService#place} treats the unknown face as used: not on its wall, and the next tunnel skips its face. */
    static void placementRefused(GameTestHelper helper) {
        Setup setup = setup(helper);
        Room unknown = decode(helper, setup.saved);
        RoomData.get(helper.getLevel().getServer()).replace(unknown);
        RoomBuilder.build(setup.rooms, unknown);
        TunnelService service = TunnelService.get();

        PlaceResult onUnknown = service.place(setup.rooms, setup.wall, ModTunnelKinds.TRANSFER.get(), TunnelMode.PASSTHROUGH, null);
        helper.assertTrue(onUnknown instanceof PlaceResult.Refused, "placing on the unknown wall should be refused, got " + onUnknown);

        BlockPos other = unknown.geometry().wallCenter(Direction.SOUTH);
        PlaceResult next = service.place(setup.rooms, other, ModTunnelKinds.TRANSFER.get(), TunnelMode.PASSTHROUGH, null);
        helper.assertValueEqual(next, new PlaceResult.Placed(Direction.UP), "the next tunnel skips the unknown face");
        Room after = TestRooms.room(helper, setup.machine);
        helper.assertValueEqual(after.unknownKind(FACE), MISSING, "the unknown entry is untouched");
        helper.succeed();
    }

    /**
     * A room saved with the Create addon's kinetic tunnel ({@code tinytunnels_create:kinetic}), as a world from
     * before the addon was removed. With only core, the entry is kept as an unknown kind and the shell repair puts
     * the inert wall there. In the addon's run, where the kind is registered, it decodes as a tunnel instead.
     */
    static void createEntry(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, TunnelGameTests.MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = room.geometry().wallCenter(Direction.EAST);
        CompoundTag entry = new CompoundTag();
        entry.putString("kind", CREATE_KINETIC.toString());
        entry.put("wall", NbtUtils.writeBlockPos(wall));
        entry.putString("data", "out");
        CompoundTag saved = encode(helper, room);
        CompoundTag tunnels = saved.getCompound("tunnels");
        tunnels.put(Direction.EAST.getSerializedName(), entry);
        saved.put("tunnels", tunnels);

        Room loaded = decode(helper, saved);
        helper.assertTrue(loaded.isFaceUsed(Direction.EAST), "the face counts as used");
        if (TunnelKinds.REGISTRY.containsKey(CREATE_KINETIC)) {
            helper.assertTrue(loaded.unknownKind(Direction.EAST) == null, "with the addon, the entry decodes as a tunnel");
            helper.succeed();
            return;
        }
        helper.assertValueEqual(loaded.unknownKind(Direction.EAST), CREATE_KINETIC, "kept as an unknown kind");
        helper.assertValueEqual(encode(helper, loaded), saved, "saved back unchanged");
        RoomData.get(helper.getLevel().getServer()).replace(loaded);
        RoomBuilder.build(rooms, loaded);
        helper.assertTrue(rooms.getBlockState(wall).is(ModBlocks.UNKNOWN_TUNNEL_WALL.get()),
                "the shell repair should put the unknown wall there, got " + rooms.getBlockState(wall));
        helper.succeed();
    }

    /**
     * A6.9: a room chunk loads with an unknown tunnel's wall as air (an unregistered mod's block becomes air on load,
     * with no {@code onRemove}, so nothing queued a repair), and a plain room wall as air too. The chunk load has to
     * repair both: the unknown wall back at the tunnel, the room wall back at the other spot. The load is faked by
     * posting {@code ChunkEvent.Load} for that chunk. (Driving the player's first entry instead isn't possible here:
     * a mock server player's dimension change throws in Jade's and Mekanism's join payloads.)
     */
    static void chunkLoadRepairs(GameTestHelper helper) {
        Setup setup = setup(helper);
        Room unknown = decode(helper, setup.saved);
        RoomData.get(helper.getLevel().getServer()).replace(unknown);
        BlockPos plain = unknown.geometry().wallCenter(Direction.SOUTH);
        ShellProtection.edit(() -> {
            setup.rooms.setBlock(setup.wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            setup.rooms.setBlock(plain, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        helper.assertTrue(setup.rooms.getBlockState(setup.wall).isAir(), "the tunnel's wall position should start as air");
        helper.assertTrue(setup.rooms.getBlockState(plain).isAir(), "the plain wall position should start as air");
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(setup.rooms.getChunkAt(setup.wall), false));
        if (!new ChunkPos(plain).equals(new ChunkPos(setup.wall))) NeoForge.EVENT_BUS.post(new ChunkEvent.Load(setup.rooms.getChunkAt(plain), false));
        helper.succeedWhen(() -> {
            helper.assertTrue(setup.rooms.getBlockState(setup.wall).is(ModBlocks.UNKNOWN_TUNNEL_WALL.get()),
                    "the chunk load should put the unknown wall there, got " + setup.rooms.getBlockState(setup.wall));
            helper.assertTrue(setup.rooms.getBlockState(plain).is(ModBlocks.ROOM_WALL.get()),
                    "the chunk load should put the room wall back, got " + setup.rooms.getBlockState(plain));
        });
    }

    private record Setup(MachineHost machine, ServerLevel rooms, BlockPos wall, CompoundTag saved) {}

    /** A machine with a test-kind tunnel on {@link #FACE}, saved with its kind id swapped for {@link #MISSING}. */
    private static Setup setup(GameTestHelper helper) {
        MachineHost machine = TestRooms.placeMachine(helper, TunnelGameTests.MACHINE);
        Room room = TestRooms.room(helper, machine);
        ServerLevel rooms = TestRooms.rooms(helper);
        BlockPos wall = room.geometry().wallCenter(Direction.NORTH);
        RoomData data = RoomData.get(helper.getLevel().getServer());
        data.setTunnel(room.id(), FACE, new RoomTunnel<>(TestTunnelKind.KIND.get(), wall, DATA));
        Room withTest = TestRooms.room(helper, machine);
        RoomBuilder.build(rooms, withTest);
        helper.assertTrue(rooms.getBlockState(wall).is(Blocks.REINFORCED_DEEPSLATE), "the test kind's wall should be built");
        return new Setup(machine, rooms, wall, swapKind(encode(helper, withTest), MISSING));
    }

    /** Saves the room the way {@code RoomData} does, with the registries in the ops. */
    private static CompoundTag encode(GameTestHelper helper, Room room) {
        Tag tag = Room.CODEC.encodeStart(ops(helper), room).getOrThrow();
        return (CompoundTag) tag;
    }

    private static Room decode(GameTestHelper helper, CompoundTag tag) {
        return Room.CODEC.parse(ops(helper), tag.copy()).getOrThrow();
    }

    private static DynamicOps<Tag> ops(GameTestHelper helper) {
        return helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static CompoundTag swapKind(CompoundTag room, ResourceLocation kind) {
        CompoundTag copy = room.copy();
        copy.getCompound("tunnels").getCompound(FACE.getSerializedName()).putString("kind", kind.toString());
        return copy;
    }

    private UnknownKindGameTests() {}
}
