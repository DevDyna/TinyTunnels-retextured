package dev.thefern2.tinytunnels.room;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.tunnel.RedstoneSignal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;

/**
 * One room. Immutable: {@link RoomData} swaps in a new instance on every change.
 *
 * @param host  where the machine bound to this room currently stands; empty while the machine is an item
 * @param faces outer face of the machine -> the tunnel on it, of any kind. Saved as {@code tunnels}.
 * @param entry where the last player to leave with the Shrinker stood; the next entry starts there if it's still clear
 */
public record Room(UUID id, int gridIndex, int size, Optional<GlobalPos> host, Map<Direction, RoomTunnel<?>> faces, Optional<EntryPoint> entry) {
    /**
     * Writes {@code tunnels} as one map of face -> {@link RoomTunnel}. Reads the dev-world format once too: an old
     * {@code tunnels} entry is a bare wall position (a pass-through transfer tunnel; buffered modes lived on the wall
     * then, so they come back as pass-through), and the old {@code redstone} and {@code kinetic} maps are folded in.
     */
    public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Room::id),
            Codec.INT.fieldOf("grid_index").forGetter(Room::gridIndex),
            Codec.INT.fieldOf("size").forGetter(Room::size),
            GlobalPos.CODEC.optionalFieldOf("host").forGetter(Room::host),
            Codec.unboundedMap(Direction.CODEC, Codec.either(RoomTunnel.CODEC, BlockPos.CODEC)).optionalFieldOf("tunnels", Map.of())
                    .forGetter(Room::savedTunnels),
            Codec.unboundedMap(Direction.CODEC, OldRedstone.CODEC).optionalFieldOf("redstone", Map.of()).forGetter(room -> Map.of()),
            Codec.unboundedMap(Direction.CODEC, OldKinetic.CODEC).optionalFieldOf("kinetic", Map.of()).forGetter(room -> Map.of()),
            EntryPoint.CODEC.optionalFieldOf("entry").forGetter(Room::entry)
    ).apply(i, Room::fromSaved));

    public Room {
        faces = faces.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(faces));
    }

    private static Room fromSaved(UUID id, int gridIndex, int size, Optional<GlobalPos> host, Map<Direction, Either<RoomTunnel<?>, BlockPos>> tunnels,
                                  Map<Direction, OldRedstone> redstone, Map<Direction, OldKinetic> kinetic, Optional<EntryPoint> entry) {
        Map<Direction, RoomTunnel<?>> faces = new EnumMap<>(Direction.class);
        tunnels.forEach((face, saved) -> faces.put(face, saved.map(tunnel -> tunnel, wall -> new RoomTunnel<>(ModTunnelKinds.TRANSFER.get(), wall, TunnelMode.PASSTHROUGH))));
        redstone.forEach((face, tunnel) -> faces.putIfAbsent(face, new RoomTunnel<>(ModTunnelKinds.REDSTONE.get(), tunnel.pos(), new RedstoneSignal(tunnel.mode(), tunnel.power()))));
        kinetic.forEach((face, tunnel) -> faces.putIfAbsent(face, new RoomTunnel<>(ModTunnelKinds.KINETIC.get(), tunnel.pos(), tunnel.mode())));
        return new Room(id, gridIndex, size, host, faces, entry);
    }

    private Map<Direction, Either<RoomTunnel<?>, BlockPos>> savedTunnels() {
        Map<Direction, Either<RoomTunnel<?>, BlockPos>> saved = new EnumMap<>(Direction.class);
        faces.forEach((face, tunnel) -> saved.put(face, Either.left(tunnel)));
        return saved;
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
        return new Room(id, gridIndex, size, host, faces, entry);
    }

    public Room withFaces(Map<Direction, RoomTunnel<?>> faces) {
        return new Room(id, gridIndex, size, host, faces, entry);
    }

    public Room withEntry(Optional<EntryPoint> entry) {
        return new Room(id, gridIndex, size, host, faces, entry);
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

    /** True if the face has a tunnel of any kind. */
    public boolean isFaceUsed(Direction face) {
        return faces.containsKey(face);
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

    // The dev-world format's redstone and kinetic maps, only for reading old saves.

    private record OldRedstone(BlockPos pos, RedstoneMode mode, int power) {
        static final Codec<OldRedstone> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(OldRedstone::pos),
                RedstoneMode.CODEC.fieldOf("mode").forGetter(OldRedstone::mode),
                ExtraCodecs.intRange(0, 15).optionalFieldOf("power", 0).forGetter(OldRedstone::power)
        ).apply(i, OldRedstone::new));
    }

    private record OldKinetic(BlockPos pos, RedstoneMode mode) {
        static final Codec<OldKinetic> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(OldKinetic::pos),
                RedstoneMode.CODEC.fieldOf("mode").forGetter(OldKinetic::mode)
        ).apply(i, OldKinetic::new));
    }
}
