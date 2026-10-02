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
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.TunnelKind;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Every room on the server. The only place room state is changed; every change marks the data dirty. */
public class RoomData extends SavedData {
    private static final Codec<RoomData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Room.CODEC.listOf().fieldOf("rooms").forGetter(data -> List.copyOf(data.rooms.values())),
            Codec.INT.fieldOf("next_grid_index").forGetter(data -> data.nextGridIndex)
    ).apply(i, RoomData::new));

    private static final SavedData.Factory<RoomData> FACTORY = new SavedData.Factory<>(RoomData::new, RoomData::load, null);
    private static final String FILE_NAME = TinyTunnels.MODID + "_rooms";

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
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_NAME);
    }

    private static RoomData load(CompoundTag tag, HolderLookup.Provider registries) {
        return CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .resultOrPartial(error -> TinyTunnels.LOGGER.error("Couldn't load rooms: {}", error))
                .orElseGet(RoomData::new);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), this).getOrThrow() instanceof CompoundTag encoded) {
            tag.merge(encoded);
        }
        return tag;
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
    public boolean wouldNestInItself(UUID roomId, GlobalPos pos, ResourceKey<Level> roomDimension) {
        GlobalPos at = pos;
        for (int depth = 0; depth < MAX_NESTING && at.dimension() == roomDimension; depth++) {
            Room container = byChunk(new ChunkPos(at.pos().getX() >> 4, at.pos().getZ() >> 4)).orElse(null);
            if (container == null) return false;
            if (container.id().equals(roomId)) return true;
            if (container.host().isEmpty()) return false;
            at = container.host().get();
        }
        return false;
    }

    /** Records {@code tunnel} on {@code face}, replacing whatever was on that face or at that wall. */
    public void setTunnel(UUID id, Direction face, RoomTunnel<?> tunnel) {
        updateFaces(id, faces -> {
            faces.values().removeIf(other -> other.wall().equals(tunnel.wall()));
            faces.put(face, tunnel);
        });
    }

    /** Removes the tunnel on {@code face}, of any kind. */
    public void removeTunnel(UUID id, Direction face) {
        updateFaces(id, faces -> faces.remove(face));
    }

    /** Moves the tunnel on {@code face} to {@code newFace}, keeping its wall and data. Nothing happens if {@code newFace} is used. */
    public void moveTunnel(UUID id, Direction face, Direction newFace) {
        updateFaces(id, faces -> {
            if (faces.containsKey(newFace)) return;
            RoomTunnel<?> tunnel = faces.remove(face);
            if (tunnel != null) faces.put(newFace, tunnel);
        });
    }

    /**
     * Moves every tunnel (unknown kinds too) from its face to {@code turn.apply(face)}, all at once, keeping walls and
     * data. {@code turn} must be one-to-one, so no two tunnels end up on one face.
     */
    public void turnFaces(UUID id, UnaryOperator<Direction> turn) {
        update(id, room -> {
            Map<Direction, RoomTunnel<?>> faces = new EnumMap<>(Direction.class);
            room.faces().forEach((face, tunnel) -> faces.put(turn.apply(face), tunnel));
            Map<Direction, UnknownTunnel> unknown = new EnumMap<>(Direction.class);
            room.unknown().forEach((face, tunnel) -> unknown.put(turn.apply(face), tunnel));
            return new Room(room.id(), room.gridIndex(), room.size(), room.host(), faces, unknown, room.entry());
        });
    }

    /** Changes the data of the tunnel on {@code face}, if it's of {@code kind}. */
    public <D> void setData(UUID id, Direction face, TunnelKind<D> kind, D data) {
        updateFaces(id, faces -> {
            RoomTunnel<?> tunnel = faces.get(face);
            if (tunnel != null) tunnel.as(kind).ifPresent(typed -> faces.put(face, typed.withData(data)));
        });
    }

    private void updateFaces(UUID id, Consumer<Map<Direction, RoomTunnel<?>>> change) {
        update(id, room -> {
            Map<Direction, RoomTunnel<?>> faces = new EnumMap<>(Direction.class);
            faces.putAll(room.faces());
            change.accept(faces);
            return room.withFaces(faces);
        });
    }

    public void setEntry(UUID id, EntryPoint entry) {
        update(id, room -> room.withEntry(Optional.of(entry)));
    }

    /** Reserves a grid slot: the lowest one a deleted room freed, else a new one. The caller builds the walls. */
    public Room allocate(int size) {
        int gridIndex = 0;
        while (gridIndex < nextGridIndex && byGridIndex.containsKey(gridIndex)) gridIndex++;
        if (gridIndex == nextGridIndex) nextGridIndex++;
        Room room = new Room(UUID.randomUUID(), gridIndex, size, Optional.empty(), Map.of(), Map.of(), Optional.empty());
        put(room);
        setDirty();
        return room;
    }

    /** Forgets a room and frees its grid slot. The caller clears its blocks first. */
    public void delete(UUID id) {
        Room room = rooms.remove(id);
        if (room == null) return;
        byGridIndex.remove(room.gridIndex());
        setDirty();
    }

    /**
     * Puts {@code room} in place of the room with its id, as it is (for example one decoded from a save). Its grid
     * slot must be the one it already has. For GameTests and repairs; normal changes go through the setters.
     */
    public void replace(Room room) {
        Room old = rooms.get(room.id());
        if (old != null && old.gridIndex() != room.gridIndex()) {
            throw new IllegalArgumentException("Room " + room.id() + " is in grid slot " + old.gridIndex() + ", not " + room.gridIndex());
        }
        put(room);
        setDirty();
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
