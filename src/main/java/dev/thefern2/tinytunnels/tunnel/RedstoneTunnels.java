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
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.RedstoneTunnel;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.wall.ShellProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
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
        RedstoneTunnel tunnel = machine.hostedRoom().map(room -> room.redstone().get(face)).orElse(null);
        return tunnel != null && tunnel.mode() == RedstoneMode.OUT ? tunnel.power() : 0;
    }

    /**
     * What the wall emits towards a receiver asking in {@code direction} (which points from the
     * receiver to the wall): the stored signal of an IN tunnel, on its inward side only.
     */
    static int wallOutput(BlockGetter level, BlockPos pos, BlockState state, Direction direction) {
        if (direction.getOpposite() != state.getValue(RedstoneTunnelWallBlock.INWARD) || !(level instanceof ServerLevel rooms)) return 0;
        RedstoneTunnel tunnel = mapped(rooms, pos, state);
        return tunnel != null && tunnel.mode() == RedstoneMode.IN ? tunnel.power() : 0;
    }

    /**
     * Tells the blocks around an emitter that its signal on {@code side} changed, including the
     * neighbours of the block it strongly powers, as a repeater does.
     */
    static void notifyEmitter(ServerLevel level, BlockPos pos, Direction side, Block block) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAtExceptFromFacing(pos.relative(side), block, side.getOpposite(), null);
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
        for (Map.Entry<Direction, RedstoneTunnel> entry : room.redstone().entrySet()) {
            if (entry.getValue().mode() != RedstoneMode.IN) continue;
            int power = readInput(level, machine.getBlockPos(), entry.getKey());
            if (power == entry.getValue().power()) continue;
            RoomData.get(level.getServer()).setRedstonePower(room.id(), entry.getKey(), power);
            updateWall(level.getServer(), room.id(), entry.getKey());
            updateMachineLook(level.getServer(), room.id());
        }
    }

    /** The wall's scheduled tick: for an OUT tunnel, reads the room side and passes changes to the machine. */
    static void readWallInput(ServerLevel rooms, BlockPos pos, BlockState state) {
        Room room = roomAt(rooms, pos);
        Direction face = state.getValue(RedstoneTunnelWallBlock.FACE);
        RedstoneTunnel tunnel = mapped(rooms, pos, state);
        if (room == null || tunnel == null || tunnel.mode() != RedstoneMode.OUT) return;
        int power = readInput(rooms, pos, state.getValue(RedstoneTunnelWallBlock.INWARD));
        if (power == tunnel.power()) return;
        RoomData.get(rooms.getServer()).setRedstonePower(room.id(), face, power);
        updateWall(rooms.getServer(), room.id(), face);
        updateMachine(rooms.getServer(), room.id(), face);
    }

    // Keeping both ends in step

    /** Syncs the wall's look with the stored tunnel and tells its neighbours. Skipped if the wall isn't loaded. */
    static void updateWall(MinecraftServer server, UUID roomId, Direction face) {
        ServerLevel rooms = RoomDimension.getRoomLevel(server);
        Room room = RoomData.get(server).room(roomId).orElse(null);
        RedstoneTunnel tunnel = room == null ? null : room.redstone().get(face);
        if (rooms == null || tunnel == null || !rooms.isLoaded(tunnel.pos())) return;
        BlockState state = rooms.getBlockState(tunnel.pos());
        if (!(state.getBlock() instanceof RedstoneTunnelWallBlock)) return;
        Direction inward = room.geometry().inwardNormal(tunnel.pos());
        BlockState synced = state.setValue(RedstoneTunnelWallBlock.MODE, tunnel.mode())
                .setValue(RedstoneTunnelWallBlock.POWERED, tunnel.power() > 0);
        if (inward != null) synced = synced.setValue(RedstoneTunnelWallBlock.INWARD, inward);
        BlockState target = synced;
        if (target != state) ShellProtection.edit(() -> rooms.setBlock(tunnel.pos(), target, Block.UPDATE_CLIENTS));
        notifyEmitter(rooms, tunnel.pos(), target.getValue(RedstoneTunnelWallBlock.INWARD), target.getBlock());
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

    /** Lights or darkens the machine's redstone ports to match their signals. Clients only: no neighbour updates. */
    static void updateMachineLook(MinecraftServer server, UUID roomId) {
        Room room = RoomData.get(server).room(roomId).orElse(null);
        GlobalPos host = room == null ? null : room.host().orElse(null);
        ServerLevel level = host == null ? null : server.getLevel(host.dimension());
        if (level == null || !level.isLoaded(host.pos())) return;
        BlockState state = level.getBlockState(host.pos());
        if (!(state.getBlock() instanceof MachineBlock)) return;
        BlockState lit = MachineBlock.withRedstoneLit(state, room);
        if (lit != state) level.setBlock(host.pos(), lit, Block.UPDATE_CLIENTS);
    }

    /** Has the reading end of the tunnel on {@code face} read its input on the next tick. */
    static void readSoon(MinecraftServer server, UUID roomId, Direction face) {
        Room room = RoomData.get(server).room(roomId).orElse(null);
        RedstoneTunnel tunnel = room == null ? null : room.redstone().get(face);
        if (tunnel == null) return;
        if (tunnel.mode() == RedstoneMode.OUT) {
            ServerLevel rooms = RoomDimension.getRoomLevel(server);
            if (rooms != null && rooms.isLoaded(tunnel.pos()) && rooms.getBlockState(tunnel.pos()).getBlock() instanceof RedstoneTunnelWallBlock wall) {
                rooms.scheduleTick(tunnel.pos(), wall, 1);
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

    /** Right-click on the wall with an empty hand: flips the direction. The signal starts again from 0. */
    static void toggleMode(ServerLevel rooms, BlockPos pos, BlockState state, Player player) {
        Room room = roomAt(rooms, pos);
        RedstoneTunnel tunnel = mapped(rooms, pos, state);
        if (room == null || tunnel == null) return;
        Direction face = state.getValue(RedstoneTunnelWallBlock.FACE);
        RedstoneMode mode = tunnel.mode().flip();
        RoomData.get(rooms.getServer()).setRedstoneMode(room.id(), face, mode);
        tunnelChanged(rooms.getServer(), room.id(), face);
        rooms.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, mode == RedstoneMode.IN ? 0.5f : 0.55f);
        player.sendOverlayMessage(modeMessage(mode, face));
    }

    static Component modeMessage(RedstoneMode mode, Direction face) {
        return Component.translatable("message.tinytunnels.redstone_tunnel." + mode.getSerializedName(), TunnelWallBlock.faceName(face));
    }

    /** The machine is going away: the inside stops seeing what was outside it. */
    public static void machineRemoved(MinecraftServer server, Room room) {
        RoomData data = RoomData.get(server);
        room.redstone().forEach((face, tunnel) -> {
            if (tunnel.mode() != RedstoneMode.IN || tunnel.power() == 0) return;
            data.setRedstonePower(room.id(), face, 0);
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
                room.redstone().forEach((face, tunnel) -> {
                    if (tunnel.mode() == RedstoneMode.OUT) notifyEmitter(level, target.pos(), face, state.getBlock());
                });
                readMachineInputs(level, machine);
            } else if (state.getBlock() instanceof RedstoneTunnelWallBlock) {
                Room room = roomAt(level, target.pos());
                Direction face = room == null ? null : room.redstoneFaceAt(target.pos());
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
    static @Nullable RedstoneTunnel mapped(ServerLevel rooms, BlockPos pos, BlockState state) {
        Room room = roomAt(rooms, pos);
        RedstoneTunnel tunnel = room == null ? null : room.redstone().get(state.getValue(RedstoneTunnelWallBlock.FACE));
        return tunnel != null && tunnel.pos().equals(pos) ? tunnel : null;
    }

    private RedstoneTunnels() {}
}
