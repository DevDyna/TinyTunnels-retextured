package dev.thefern2.tinytunnels.tunnel;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Carries redstone through machines. Each redstone tunnel is one-way: the reading end (the machine
 * for {@link RedstoneMode#IN}, the wall for {@link RedstoneMode#OUT}) reads its neighbour on a
 * scheduled tick and stores the signal in {@link RoomData}; the emitting end answers with the stored
 * signal. One tick per hop, like a repeater, so a loop through tunnels is at worst a clock, never
 * recursion. Nothing here loads a chunk: an unloaded end catches up when it loads.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class RedstoneTunnels {
    private record Target(ServerLevel level, BlockPos pos) {}

    private static final Set<Target> REFRESH = new LinkedHashSet<>();

    // Emitting

    /** What the machine emits on {@code face}: the stored signal of an OUT redstone tunnel there. */
    public static int machineOutput(BlockGetter level, BlockPos pos, Direction face) {
        if (!(level instanceof ServerLevel server) || !(server.getBlockEntity(pos) instanceof MachineHost machine)) return 0;
        RedstoneSignal signal = machine.hostedRoom().map(room -> room.data(face, kind())).orElse(null);
        return signal != null && signal.mode() == RedstoneMode.OUT ? signal.power() : 0;
    }

    /**
     * What the wall emits towards a receiver asking in {@code direction} (which points from the
     * receiver to the wall): the stored signal of an IN tunnel, on its inward side only.
     */
    static int wallOutput(BlockGetter level, BlockPos pos, BlockState state, Direction direction) {
        if (direction.getOpposite() != state.getValue(RedstoneTunnelWallBlock.INWARD) || !(level instanceof ServerLevel rooms)) return 0;
        RoomTunnel<RedstoneSignal> tunnel = mapped(rooms, pos, state);
        return tunnel != null && tunnel.data().mode() == RedstoneMode.IN ? tunnel.data().power() : 0;
    }

    /**
     * Tells the blocks around an emitter that its signal on {@code side} changed, including the
     * neighbours of the block it strongly powers, as a repeater does.
     */
    static void notifyEmitter(ServerLevel level, BlockPos pos, Direction side, Block block) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAtExceptFromFacing(pos.relative(side), block, side.getOpposite());
    }

    // Reading

    /** The signal coming into {@code pos} from its {@code side}. Dust counts even when it doesn't point at us, as for repeaters. */
    static int readInput(Level level, BlockPos pos, Direction side) {
        BlockPos from = pos.relative(side);
        int power = level.getSignal(from, side);
        if (power >= 15) return power;
        BlockState state = level.getBlockState(from);
        return state.is(Blocks.REDSTONE_WIRE) ? Math.max(power, state.getValue(RedStoneWireBlock.POWER)) : power;
    }

    /** The machine's scheduled tick: reads each IN face and passes changes into the room. */
    public static void readMachineInputs(ServerLevel level, MachineHost machine) {
        Room room = machine.hostedRoom().orElse(null);
        if (room == null) return;
        for (Map.Entry<Direction, RoomTunnel<?>> entry : room.faces().entrySet()) {
            RedstoneSignal signal = room.data(entry.getKey(), kind());
            if (signal == null || signal.mode() != RedstoneMode.IN) continue;
            int power = readInput(level, machine.getBlockPos(), entry.getKey());
            if (power == signal.power()) continue;
            setPower(level.getServer(), room.id(), entry.getKey(), signal.withPower(power));
            updateWall(level.getServer(), room.id(), entry.getKey());
            updateMachineLook(level.getServer(), room.id());
        }
    }

    /** The wall's scheduled tick: for an OUT tunnel, reads the room side and passes changes to the machine. */
    static void readWallInput(ServerLevel rooms, BlockPos pos, BlockState state) {
        Room room = roomAt(rooms, pos);
        Direction face = state.getValue(RedstoneTunnelWallBlock.FACE);
        RoomTunnel<RedstoneSignal> tunnel = mapped(rooms, pos, state);
        if (room == null || tunnel == null || tunnel.data().mode() != RedstoneMode.OUT) return;
        int power = readInput(rooms, pos, state.getValue(RedstoneTunnelWallBlock.INWARD));
        if (power == tunnel.data().power()) return;
        setPower(rooms.getServer(), room.id(), face, tunnel.data().withPower(power));
        updateWall(rooms.getServer(), room.id(), face);
        updateMachine(rooms.getServer(), room.id(), face);
    }

    // Keeping both ends in step

    /** Syncs the wall's look with the stored tunnel and tells its neighbours. Skipped if the wall isn't loaded. */
    static void updateWall(MinecraftServer server, UUID roomId, Direction face) {
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        Room room = RoomData.get(server).room(roomId).orElse(null);
        RoomTunnel<RedstoneSignal> tunnel = room == null ? null : room.tunnel(face, kind());
        if (rooms == null || tunnel == null || !rooms.isLoaded(tunnel.wall())) return;
        BlockPos wall = tunnel.wall();
        BlockState state = rooms.getBlockState(wall);
        if (!(state.getBlock() instanceof RedstoneTunnelWallBlock)) return;
        Direction inward = room.geometry().inwardNormal(wall);
        BlockState synced = state.setValue(RedstoneTunnelWallBlock.MODE, tunnel.data().mode())
                .setValue(RedstoneTunnelWallBlock.POWERED, tunnel.data().power() > 0);
        if (inward != null) synced = synced.setValue(RedstoneTunnelWallBlock.INWARD, inward);
        BlockState target = synced;
        if (target != state) ShellProtection.edit(() -> rooms.setBlock(wall, target, Block.UPDATE_CLIENTS));
        notifyEmitter(rooms, wall, target.getValue(RedstoneTunnelWallBlock.INWARD), target.getBlock());
    }

    /** Tells the blocks around the machine's {@code face} that its signal there changed. Skipped if the machine isn't loaded. */
    static void updateMachine(MinecraftServer server, UUID roomId, Direction face) {
        GlobalPos host = RoomData.get(server).room(roomId).flatMap(Room::host).orElse(null);
        ServerLevel level = host == null ? null : server.getLevel(host.dimension());
        if (level == null || !level.isLoaded(host.pos())) return;
        BlockState state = level.getBlockState(host.pos());
        if (!(state.getBlock() instanceof MachineBlock)) return;
        updateMachineLook(server, roomId);
        notifyEmitter(level, host.pos(), face, state.getBlock());
    }

    /** Lights or darkens the machine's redstone faces to match their signals (the face looks resend only on a change). */
    static void updateMachineLook(MinecraftServer server, UUID roomId) {
        Room room = RoomData.get(server).room(roomId).orElse(null);
        GlobalPos host = room == null ? null : room.host().orElse(null);
        ServerLevel level = host == null ? null : server.getLevel(host.dimension());
        if (level == null || !level.isLoaded(host.pos())) return;
        BlockState state = level.getBlockState(host.pos());
        if (!(state.getBlock() instanceof MachineBlock)) return;
        if (level.getBlockEntity(host.pos()) instanceof MachineHost machine) machine.core().syncFaces();
    }

    /** Has the reading end of the tunnel on {@code face} read its input on the next tick. */
    static void readSoon(MinecraftServer server, UUID roomId, Direction face) {
        Room room = RoomData.get(server).room(roomId).orElse(null);
        RoomTunnel<RedstoneSignal> tunnel = room == null ? null : room.tunnel(face, kind());
        if (tunnel == null) return;
        if (tunnel.data().mode() == RedstoneMode.OUT) {
            ServerLevel rooms = RoomDimension.getRoomLevel(server);
            if (rooms != null && rooms.isLoaded(tunnel.wall()) && rooms.getBlockState(tunnel.wall()).getBlock() instanceof RedstoneTunnelWallBlock wall) {
                rooms.scheduleTick(tunnel.wall(), wall, 1);
            }
        } else {
            GlobalPos host = room.host().orElse(null);
            ServerLevel level = host == null ? null : server.getLevel(host.dimension());
            if (level != null && level.isLoaded(host.pos()) && level.getBlockState(host.pos()).getBlock() instanceof MachineBlock machine) {
                level.scheduleTick(host.pos(), machine, 1);
            }
        }
    }

    /** Both ends of the tunnel on {@code face} changed: update both and have the reading end read. */
    public static void tunnelChanged(MinecraftServer server, UUID roomId, Direction face) {
        updateWall(server, roomId, face);
        updateMachine(server, roomId, face);
        readSoon(server, roomId, face);
    }

    static Component modeMessage(RedstoneMode mode, Direction face) {
        return Component.translatable("message.tinytunnels.redstone_tunnel." + mode.getSerializedName(), TunnelWallBlock.faceName(face));
    }

    /** The machine is going away: the inside stops seeing what was outside it. */
    public static void machineRemoved(MinecraftServer server, Room room) {
        room.faces().keySet().forEach(face -> {
            RedstoneSignal signal = room.data(face, kind());
            if (signal == null || signal.mode() != RedstoneMode.IN || signal.power() == 0) return;
            setPower(server, room.id(), face, signal.withPower(0));
            updateWall(server, room.id(), face);
        });
    }

    // Catching up after a load

    /** Brings a machine or redstone wall up to date at the end of this tick (not during chunk loading). */
    public static void refreshLater(ServerLevel level, BlockPos pos) {
        REFRESH.add(new Target(level, pos.immutable()));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (REFRESH.isEmpty()) return;
        List<Target> targets = List.copyOf(REFRESH);
        REFRESH.clear();
        MinecraftServer server = event.getServer();
        for (Target target : targets) {
            ServerLevel level = target.level();
            if (!level.isLoaded(target.pos())) continue;
            BlockState state = level.getBlockState(target.pos());
            if (level.getBlockEntity(target.pos()) instanceof MachineHost machine) {
                Room room = machine.hostedRoom().orElse(null);
                if (room == null) continue;
                room.faces().keySet().forEach(face -> {
                    RedstoneSignal signal = room.data(face, kind());
                    if (signal != null && signal.mode() == RedstoneMode.OUT) notifyEmitter(level, target.pos(), face, state.getBlock());
                });
                readMachineInputs(level, machine);
            } else if (state.getBlock() instanceof RedstoneTunnelWallBlock) {
                Room room = roomAt(level, target.pos());
                Direction face = room == null ? null : room.faceAt(target.pos(), ModTunnelKinds.REDSTONE.get());
                if (face == null) continue;
                updateWall(server, room.id(), face);
                readSoon(server, room.id(), face);
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        REFRESH.clear();
    }

    // Lookups

    private static @Nullable Room roomAt(ServerLevel rooms, BlockPos pos) {
        return RoomData.get(rooms.getServer()).byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).orElse(null);
    }

    /** The redstone tunnel this wall is, if the room still maps its face to this position. */
    static @Nullable RoomTunnel<RedstoneSignal> mapped(ServerLevel rooms, BlockPos pos, BlockState state) {
        Room room = roomAt(rooms, pos);
        RoomTunnel<RedstoneSignal> tunnel = room == null ? null : room.tunnel(state.getValue(RedstoneTunnelWallBlock.FACE), kind());
        return tunnel != null && tunnel.wall().equals(pos) ? tunnel : null;
    }

    private static RedstoneTunnelKind kind() {
        return ModTunnelKinds.REDSTONE.get();
    }

    /** A new reading: recorded quietly (this class keeps the wall and the machine in step itself). */
    private static void setPower(MinecraftServer server, UUID roomId, Direction face, RedstoneSignal signal) {
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        if (rooms != null) {
            TunnelChanges.recordData(rooms, roomId, face, kind(), signal);
        } else {
            RoomData.get(server).setData(roomId, face, kind(), signal);
        }
    }

    private RedstoneTunnels() {}
}
