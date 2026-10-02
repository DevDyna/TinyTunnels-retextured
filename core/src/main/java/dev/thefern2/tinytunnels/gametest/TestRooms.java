package dev.thefern2.tinytunnels.gametest;

import dev.thefern2.tinytunnels.machine.MachineFaces;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomGeometry;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.room.TunnelMode;
import dev.thefern2.tinytunnels.tunnel.CapabilityUpdates;
import dev.thefern2.tinytunnels.tunnel.RedstoneSignal;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnels;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Builds machines, rooms and tunnels in code, the way a player would, for GameTests. */
final class TestRooms {
    /** A machine placed and bound like a player placing it, in any level. */
    static MachineHost placeMachine(ServerLevel level, BlockPos pos, MachineSize size) {
        level.setBlock(pos, ModBlocks.MACHINES.get(size).get().defaultBlockState(), Block.UPDATE_ALL);
        MachineHost machine = (MachineHost) level.getBlockEntity(pos);
        machine.bindOnPlace(level, size);
        return machine;
    }

    static MachineHost placeMachine(GameTestHelper helper, BlockPos relative) {
        return placeMachine(helper.getLevel(), helper.absolutePos(relative), MachineSize.NORMAL);
    }

    /**
     * What the machine at {@code pos} (absolute) last sent its clients for each face: the tunnel kind and look.
     * {@link MachineFaces#NONE} if there's no machine there.
     */
    static MachineFaces faces(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachineHost machine ? machine.core().faces() : MachineFaces.NONE;
    }

    static MachineFaces faces(GameTestHelper helper, BlockPos relative) {
        return faces(helper.getLevel(), helper.absolutePos(relative));
    }

    static ServerLevel rooms(GameTestHelper helper) {
        return RoomDimension.getRoomLevel(helper.getLevel().getServer());
    }

    static Room room(GameTestHelper helper, MachineHost machine) {
        return RoomData.get(helper.getLevel().getServer()).room(machine.getRoomId()).orElseThrow();
    }

    /** The middle block of the room's wall (or floor/ceiling) on {@code side}. */
    static BlockPos wallCenter(RoomGeometry geometry, Direction side) {
        return geometry.wallCenter(side);
    }

    /** Puts a tunnel for machine face {@code face} at {@code wallPos}. Returns the inside block next to it. */
    static BlockPos addTunnel(GameTestHelper helper, Room room, Direction face, BlockPos wallPos) {
        ServerLevel rooms = rooms(helper);
        Direction inward = room.geometry().inwardNormal(wallPos);
        ShellProtection.edit(() -> rooms.setBlock(wallPos, ModBlocks.TUNNEL_WALL.get().defaultBlockState()
                .setValue(TunnelWallBlock.FACE, face).setValue(TunnelWallBlock.INWARD, inward), Block.UPDATE_ALL));
        RoomData data = RoomData.get(helper.getLevel().getServer());
        // A transfer tunnel already at this wall keeps its mode, as placing one there does.
        TunnelKind<TunnelMode> kind = ModTunnelKinds.TRANSFER.get();
        Room current = data.room(room.id()).orElseThrow();
        Direction old = current.faceAt(wallPos, kind);
        TunnelMode mode = old == null ? kind.defaultData() : current.data(old, kind);
        data.setTunnel(room.id(), face, new RoomTunnel<>(kind, wallPos, mode));
        CapabilityUpdates.roomChanged(helper.getLevel().getServer(), data.room(room.id()).orElseThrow());
        return wallPos.relative(inward);
    }

    /**
     * Puts a redstone tunnel for machine face {@code face} at {@code wallPos}, the way the Redstone
     * Tunnel item does. Returns the inside block next to it.
     */
    static BlockPos addRedstoneTunnel(GameTestHelper helper, Room room, Direction face, BlockPos wallPos, RedstoneMode mode) {
        ServerLevel rooms = rooms(helper);
        Direction inward = room.geometry().inwardNormal(wallPos);
        ShellProtection.edit(() -> rooms.setBlock(wallPos, ModBlocks.REDSTONE_TUNNEL_WALL.get().defaultBlockState()
                .setValue(RedstoneTunnelWallBlock.FACE, face).setValue(RedstoneTunnelWallBlock.INWARD, inward)
                .setValue(RedstoneTunnelWallBlock.MODE, mode), Block.UPDATE_ALL));
        RoomData.get(helper.getLevel().getServer()).setTunnel(room.id(), face,
                new RoomTunnel<>(ModTunnelKinds.REDSTONE.get(), wallPos, new RedstoneSignal(mode, 0)));
        CapabilityUpdates.syncFaces(room.id());
        RedstoneTunnels.tunnelChanged(helper.getLevel().getServer(), room.id(), face);
        return wallPos.relative(inward);
    }

    /** The stored signal of the redstone tunnel on {@code face}, or -1 if there is none. */
    static int redstonePower(GameTestHelper helper, Room room, Direction face) {
        RedstoneSignal signal = RoomData.get(helper.getLevel().getServer()).room(room.id()).orElseThrow().data(face, ModTunnelKinds.REDSTONE.get());
        return signal == null ? -1 : signal.power();
    }

    static void removeTunnel(GameTestHelper helper, Room room, Direction face) {
        RoomData data = RoomData.get(helper.getLevel().getServer());
        Room current = data.room(room.id()).orElseThrow();
        RoomTunnel<TunnelMode> tunnel = current.tunnel(face, ModTunnelKinds.TRANSFER.get());
        if (tunnel == null) return;
        BlockPos wallPos = tunnel.wall();
        ShellProtection.edit(() -> rooms(helper).setBlock(wallPos, ModBlocks.ROOM_WALL.get().defaultBlockState(), Block.UPDATE_ALL));
        data.removeTunnel(room.id(), face);
        CapabilityUpdates.roomChanged(helper.getLevel().getServer(), current);
    }

    /** Inserts for real across all slots; returns how many went in. */
    static int insert(IItemHandler handler, ItemStack stack) {
        return stack.getCount() - ItemHandlerHelper.insertItem(handler, stack.copy(), false).getCount();
    }

    /** Fills for real; returns how much went in. */
    static int fill(IFluidHandler handler, FluidStack stack) {
        return handler.fill(stack, IFluidHandler.FluidAction.EXECUTE);
    }

    private TestRooms() {}
}
