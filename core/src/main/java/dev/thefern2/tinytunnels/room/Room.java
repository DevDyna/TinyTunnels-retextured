package dev.thefern2.tinytunnels.room;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * One room. Immutable: {@link RoomData} swaps in a new instance on every change.
 *
 * @param host  where the machine bound to this room currently stands; empty while the machine is an item
 * @param faces   outer face of the machine -> the tunnel on it, of any registered kind. Saved as {@code tunnels}.
 * @param unknown outer face -> a saved tunnel of a kind that isn't registered, kept as it was ({@link UnknownTunnel}).
 *                Saved in {@code tunnels} too; a face is in at most one of the two maps.
 * @param entry where the last player to leave with the Shrinker stood; the next entry starts there if it's still clear
 */
public record Room(UUID id, int gridIndex, int size, Optional<GlobalPos> host, Map<Direction, RoomTunnel<?>> faces,
                   Map<Direction, UnknownTunnel> unknown, Optional<EntryPoint> entry) {
    /** Writes {@code tunnels} as one map of face -> {@link RoomTunnel}, with unknown kinds' entries written back as they were loaded. */
    public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Room::id),
            Codec.INT.fieldOf("grid_index").forGetter(Room::gridIndex),
            Codec.INT.fieldOf("size").forGetter(Room::size),
            GlobalPos.CODEC.optionalFieldOf("host").forGetter(Room::host),
            Codec.unboundedMap(Direction.CODEC, Saved.CODEC).optionalFieldOf("tunnels", Map.of())
                    .forGetter(Room::savedTunnels),
            EntryPoint.CODEC.optionalFieldOf("entry").forGetter(Room::entry)
    ).apply(i, Room::fromSaved));

    public Room {
        faces = faces.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(faces));
        unknown = unknown.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(unknown));
    }

    private static Room fromSaved(UUID id, int gridIndex, int size, Optional<GlobalPos> host, Map<Direction, Saved> tunnels,
                                  Optional<EntryPoint> entry) {
        Map<Direction, RoomTunnel<?>> faces = new EnumMap<>(Direction.class);
        Map<Direction, UnknownTunnel> unknown = new EnumMap<>(Direction.class);
        tunnels.forEach((face, saved) -> {
            switch (saved) {
                case Saved.Known known -> faces.put(face, known.tunnel());
                case Saved.Raw raw -> unknown.put(face, raw.tunnel());
            }
        });
        return new Room(id, gridIndex, size, host, faces, unknown, entry);
    }

    private Map<Direction, Saved> savedTunnels() {
        Map<Direction, Saved> saved = new EnumMap<>(Direction.class);
        faces.forEach((face, tunnel) -> saved.put(face, new Saved.Known(tunnel)));
        unknown.forEach((face, tunnel) -> saved.put(face, new Saved.Raw(tunnel)));
        return saved;
    }

    /** One saved {@code tunnels} entry: a registered kind's tunnel, or an unknown kind's raw entry. */
    private sealed interface Saved {
        record Known(RoomTunnel<?> tunnel) implements Saved {}

        record Raw(UnknownTunnel tunnel) implements Saved {}


        /**
         * Reads an entry as a registered kind's tunnel; failing that, keeps it raw if it has at least a kind id and a
         * wall. Raw entries are written back exactly as they were read.
         */
        Codec<Saved> CODEC = new Codec<>() {
            @Override
            public <T> DataResult<Pair<Saved, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<Pair<RoomTunnel<?>, T>> known = RoomTunnel.CODEC.decode(ops, input);
                if (known.result().isPresent()) return known.map(pair -> Pair.of(new Saved.Known(pair.getFirst()), pair.getSecond()));
                UnknownTunnel raw = UnknownTunnel.read(new Dynamic<>(ops, input)).result().orElse(null);
                if (raw == null) return known.map(pair -> Pair.of(new Saved.Known(pair.getFirst()), pair.getSecond()));
                if (TunnelKinds.REGISTRY.containsKey(raw.kind())) {
                    TinyTunnels.LOGGER.warn("Couldn't read a {} tunnel at {}; keeping it as it was saved: {}", raw.kind(), raw.wall().toShortString(),
                            known.error().map(DataResult.Error::message).orElse("?"));
                }
                return DataResult.success(Pair.of(new Saved.Raw(raw), ops.empty()));
            }

            @Override
            public <T> DataResult<T> encode(Saved input, DynamicOps<T> ops, T prefix) {
                return switch (input) {
                    case Saved.Known known -> RoomTunnel.CODEC.encode(known.tunnel(), ops, prefix);
                    case Saved.Raw raw -> DataResult.success(raw.tunnel().saved().convert(ops).getValue());
                };
            }
        };
    }

    /** The public, read-only view of this room as it is now. */
    public RoomView view() {
        return new RoomSnapshot(this);
    }

    /** The public view of the tunnel on {@code face}, or null. */
    public @Nullable Tunnel<?> view(Direction face) {
        RoomTunnel<?> tunnel = faces.get(face);
        return tunnel == null ? null : toTunnel(face, tunnel);
    }

    public <D> Tunnel<D> toTunnel(Direction face, RoomTunnel<D> tunnel) {
        // A tunnel's wall is always a middle-of-face shell block; fall back rather than fail for a broken entry.
        return tunnel.toTunnel(face, Objects.requireNonNullElse(geometry().inwardNormal(tunnel.wall()), face.getOpposite()));
    }

    public RoomGeometry geometry() {
        return new RoomGeometry(gridIndex, size);
    }

    public Room withHost(Optional<GlobalPos> host) {
        return new Room(id, gridIndex, size, host, faces, unknown, entry);
    }

    public Room withFaces(Map<Direction, RoomTunnel<?>> faces) {
        return new Room(id, gridIndex, size, host, faces, unknown, entry);
    }

    public Room withEntry(Optional<EntryPoint> entry) {
        return new Room(id, gridIndex, size, host, faces, unknown, entry);
    }

    /** The tunnel on {@code face}, of any kind, or null. */
    public @Nullable RoomTunnel<?> at(Direction face) {
        return faces.get(face);
    }

    /** The tunnel on {@code face} if it's of {@code kind}, or null. */
    public <D> @Nullable RoomTunnel<D> tunnel(Direction face, TunnelKind<D> kind) {
        RoomTunnel<?> tunnel = faces.get(face);
        return tunnel == null ? null : tunnel.as(kind).orElse(null);
    }

    /** The data of the tunnel on {@code face} if it's of {@code kind}, or null. */
    public <D> @Nullable D data(Direction face, TunnelKind<D> kind) {
        RoomTunnel<D> tunnel = tunnel(face, kind);
        return tunnel == null ? null : tunnel.data();
    }

    /** How many tunnels of {@code kind} the room has. */
    public int count(TunnelKind<?> kind) {
        int count = 0;
        for (RoomTunnel<?> tunnel : faces.values()) {
            if (tunnel.kind() == kind) count++;
        }
        return count;
    }

    /** True if the face has a tunnel of any kind, including an unknown one. */
    public boolean isFaceUsed(Direction face) {
        return faces.containsKey(face) || unknown.containsKey(face);
    }

    /** The kind id of the unknown tunnel on {@code face}, or null if there's none (or it's a registered kind). */
    public @Nullable ResourceLocation unknownKind(Direction face) {
        UnknownTunnel tunnel = unknown.get(face);
        return tunnel == null ? null : tunnel.kind();
    }

    /** The face of the unknown tunnel whose wall is at {@code pos}, or null. */
    public @Nullable Direction unknownFaceAt(BlockPos pos) {
        for (Map.Entry<Direction, UnknownTunnel> entry : unknown.entrySet()) {
            if (entry.getValue().wall().equals(pos)) return entry.getKey();
        }
        return null;
    }

    /**
     * The next outer face without a tunnel, going round {@link Direction#values()} starting after
     * {@code after} (or from the first face when null). Null if every other face is taken.
     */
    public @Nullable Direction nextFreeFace(@Nullable Direction after) {
        Direction[] all = Direction.values();
        int start = after == null ? 0 : after.ordinal() + 1;
        for (int i = 0; i < all.length; i++) {
            Direction face = all[(start + i) % all.length];
            if (face != after && !isFaceUsed(face)) return face;
        }
        return null;
    }

    /** The face whose tunnel (of any kind) has its wall at {@code pos}, or null. */
    public @Nullable Direction faceAt(BlockPos pos) {
        for (Map.Entry<Direction, RoomTunnel<?>> entry : faces.entrySet()) {
            if (entry.getValue().wall().equals(pos)) return entry.getKey();
        }
        return null;
    }

    /** The face whose tunnel of {@code kind} has its wall at {@code pos}, or null. */
    public @Nullable Direction faceAt(BlockPos pos, TunnelKind<?> kind) {
        Direction face = faceAt(pos);
        return face != null && faces.get(face).kind() == kind ? face : null;
    }
}
