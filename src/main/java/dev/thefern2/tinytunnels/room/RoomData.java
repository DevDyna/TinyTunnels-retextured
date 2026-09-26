package dev.thefern2.tinytunnels.room;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.TinyTunnels;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Every room on the server. The only place room state is changed; every change marks the data dirty. */
public class RoomData extends SavedData {
    private static final Codec<RoomData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Room.CODEC.listOf().fieldOf("rooms").forGetter(data -> List.copyOf(data.rooms.values())),
            Codec.INT.fieldOf("next_grid_index").forGetter(data -> data.nextGridIndex)
    ).apply(i, RoomData::new));

    public static final SavedDataType<RoomData> TYPE = new SavedDataType<>(TinyTunnels.id("rooms"), RoomData::new, CODEC);

    private static final int MAX_NESTING = 32;

    private final Map<UUID, Room> rooms = new LinkedHashMap<>();
    private final Map<Integer, UUID> byGridIndex = new HashMap<>();
    private int nextGridIndex;

    public RoomData() {}

    private RoomData(List<Room> rooms, int nextGridIndex) {
        rooms.forEach(this::put);
        this.nextGridIndex = nextGridIndex;
    }

    public static RoomData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public Collection<Room> rooms() {
        return Collections.unmodifiableCollection(rooms.values());
    }

    public Optional<Room> room(UUID id) {
        return Optional.ofNullable(rooms.get(id));
    }

    /** The room whose chunk contains this position in the room dimension. */
    public Optional<Room> byChunk(ChunkPos chunk) {
        int gridIndex = RoomGeometry.gridIndexOf(chunk.getMinBlockX() >> 4, chunk.getMinBlockZ() >> 4);
        UUID id = gridIndex < 0 ? null : byGridIndex.get(gridIndex);
        return id == null ? Optional.empty() : room(id);
    }

    /**
     * True if placing a machine for {@code roomId} at {@code pos} would put it inside its own room,
     * directly or through nested rooms. Such a room would keep itself loaded forever.
     */
    public boolean wouldNestInItself(UUID roomId, GlobalPos pos) {
        GlobalPos at = pos;
        for (int depth = 0; depth < MAX_NESTING && at.dimension() == RoomDimension.ROOM_DIM; depth++) {
            Room container = byChunk(new ChunkPos(at.pos().getX() >> 4, at.pos().getZ() >> 4)).orElse(null);
            if (container == null) return false;
            if (container.id().equals(roomId)) return true;
            if (container.host().isEmpty()) return false;
            at = container.host().get();
        }
        return false;
    }

    public void setTunnel(UUID id, Direction face, BlockPos pos) {
        update(id, room -> {
            Map<Direction, BlockPos> tunnels = new EnumMap<>(Direction.class);
            tunnels.putAll(room.tunnels());
            tunnels.values().remove(pos);
            tunnels.put(face, pos.immutable());
            return room.withTunnels(tunnels);
        });
    }

    public void removeTunnel(UUID id, Direction face) {
        update(id, room -> {
            Map<Direction, BlockPos> tunnels = new EnumMap<>(Direction.class);
            tunnels.putAll(room.tunnels());
            tunnels.remove(face);
            return room.withTunnels(tunnels);
        });
    }

    /** Reserves a new grid slot. The caller builds the walls. */
    public Room allocate(int size) {
        Room room = new Room(UUID.randomUUID(), nextGridIndex++, size, Optional.empty(), Map.of());
        put(room);
        setDirty();
        return room;
    }

    public void setHost(UUID id, @Nullable GlobalPos host) {
        update(id, room -> room.withHost(Optional.ofNullable(host)));
    }

    /** Clears the host only if it still points at {@code host}, so a stale machine can't unbind a newer one. */
    public void clearHostIf(UUID id, GlobalPos host) {
        update(id, room -> room.host().filter(host::equals).isPresent() ? room.withHost(Optional.empty()) : room);
    }

    private void update(UUID id, UnaryOperator<Room> change) {
        Room room = rooms.get(id);
        if (room == null) return;
        Room updated = change.apply(room);
        if (!updated.equals(room)) {
            put(updated);
            setDirty();
        }
    }

    private void put(Room room) {
        rooms.put(room.id(), room);
        byGridIndex.put(room.gridIndex(), room.id());
    }
}
