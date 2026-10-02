package dev.thefern2.tinytunnels.tunnel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.Endpoint;
import dev.thefern2.tinytunnels.api.MachineView;
import dev.thefern2.tinytunnels.api.PlaceResult;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.machine.HostMachineView;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.room.RoomDimension;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Core's {@link TunnelService}, found by {@code TunnelService.get()} through
 * {@code META-INF/services/dev.thefern2.tinytunnels.api.TunnelService}. Every change goes to {@link TunnelChanges},
 * the same code the wrench and the tunnel items use.
 */
public final class CoreTunnelService implements TunnelService {
    /** For {@link java.util.ServiceLoader}. */
    public CoreTunnelService() {}

    @Override
    public @Nullable ServerLevel roomLevel(MinecraftServer server) {
        return RoomDimension.getRoomLevel(server);
    }

    @Override
    public Optional<MachineView> machineAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof MachineHost host ? Optional.of(new HostMachineView(host)) : Optional.empty();
    }

    @Override
    public Optional<RoomView> room(MinecraftServer server, UUID id) {
        return RoomData.get(server).room(id).map(Room::view);
    }

    @Override
    public Optional<RoomView> roomAt(ServerLevel rooms, BlockPos pos) {
        if (!RoomDimension.isRoomLevel(rooms)) return Optional.empty();
        return Optional.ofNullable(TunnelChanges.roomAt(rooms, pos)).map(Room::view);
    }

    @Override
    public List<Block> machineBlocks() {
        return ModBlocks.MACHINES.values().stream().map(block -> (Block) block.get()).toList();
    }

    @Override
    public <D> PlaceResult place(ServerLevel rooms, BlockPos wall, TunnelKind<D> kind, D data, @Nullable Player player) {
        return TunnelChanges.place(rooms, wall, kind, data, player);
    }

    @Override
    public boolean move(RoomView room, Direction face, Direction newFace) {
        ServerLevel rooms = rooms();
        return rooms != null && TunnelChanges.move(rooms, room.id(), face, newFace);
    }

    @Override
    public boolean remove(RoomView room, Direction face, @Nullable Player player) {
        ServerLevel rooms = rooms();
        return rooms != null && TunnelChanges.remove(rooms, room.id(), face, player);
    }

    @Override
    public <D> boolean setData(RoomView room, Direction face, TunnelKind<D> kind, D data) {
        ServerLevel rooms = rooms();
        return rooms != null && TunnelChanges.setData(rooms, room.id(), face, kind, data);
    }

    @Override
    public Optional<Endpoint> otherSide(Level level, BlockPos pos, Direction side) {
        if (!(level instanceof ServerLevel server)) return Optional.empty();
        if (server.getBlockEntity(pos) instanceof MachineHost machine) return insideOf(server, machine, side);
        return RoomDimension.isRoomLevel(server) ? outsideOf(server, pos, side) : Optional.empty();
    }

    /** Machine face {@code face}: the block inside the room against that face's tunnel wall, facing the wall. */
    private static Optional<Endpoint> insideOf(ServerLevel level, MachineHost machine, Direction face) {
        Room room = machine.hostedRoom().orElse(null);
        RoomTunnel<?> tunnel = room == null ? null : room.at(face);
        ServerLevel rooms = RoomDimension.getRoomLevel(level.getServer());
        if (tunnel == null || rooms == null) return Optional.empty();
        Direction inward = room.geometry().inwardNormal(tunnel.wall());
        if (inward == null) return Optional.empty();
        BlockPos inside = tunnel.wall().relative(inward);
        return rooms.isLoaded(inside) ? Optional.of(new Endpoint(rooms, inside, inward.getOpposite())) : Optional.empty();
    }

    /** A tunnel wall, asked on its inward side: the block outside against its machine face, facing the machine. */
    private static Optional<Endpoint> outsideOf(ServerLevel rooms, BlockPos wall, Direction side) {
        Room room = TunnelChanges.roomAt(rooms, wall);
        Direction face = room == null ? null : room.faceAt(wall);
        if (face == null || side != room.geometry().inwardNormal(wall)) return Optional.empty();
        GlobalPos host = room.host().orElse(null);
        ServerLevel hostLevel = host == null ? null : rooms.getServer().getLevel(host.dimension());
        if (hostLevel == null) return Optional.empty();
        BlockPos outside = host.pos().relative(face);
        return hostLevel.isLoaded(outside) ? Optional.of(new Endpoint(hostLevel, outside, face.getOpposite())) : Optional.empty();
    }

    @Override
    public <T> @Nullable T guarded(Supplier<@Nullable T> lookup) {
        if (!ProxyGuard.enter()) return null;
        try {
            return lookup.get();
        } finally {
            ProxyGuard.exit();
        }
    }

    private static @Nullable ServerLevel rooms() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : RoomDimension.getRoomLevel(server);
    }
}
