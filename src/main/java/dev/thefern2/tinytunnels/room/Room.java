package dev.thefern2.tinytunnels.room;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;

/**
 * One room. Immutable: {@link RoomData} swaps in a new instance on every change.
 *
 * @param host    where the machine bound to this room currently stands; empty while the machine is an item
 * @param tunnels  outer face of the machine -> tunnel wall position in the room dimension (items, fluids, energy)
 * @param redstone outer face of the machine -> redstone tunnel. A face is in at most one of the two maps.
 * @param entry    where the last player to leave with the Shrinker stood; the next entry starts there if it's still clear
 */
public record Room(UUID id, int gridIndex, int size, Optional<GlobalPos> host, Map<Direction, BlockPos> tunnels,
                   Map<Direction, RedstoneTunnel> redstone, Optional<EntryPoint> entry) {
    public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Room::id),
            Codec.INT.fieldOf("grid_index").forGetter(Room::gridIndex),
            Codec.INT.fieldOf("size").forGetter(Room::size),
            GlobalPos.CODEC.optionalFieldOf("host").forGetter(Room::host),
            Codec.unboundedMap(Direction.CODEC, BlockPos.CODEC).optionalFieldOf("tunnels", Map.of()).forGetter(Room::tunnels),
            Codec.unboundedMap(Direction.CODEC, RedstoneTunnel.CODEC).optionalFieldOf("redstone", Map.of()).forGetter(Room::redstone),
            EntryPoint.CODEC.optionalFieldOf("entry").forGetter(Room::entry)
    ).apply(i, Room::new));

    public Room {
        tunnels = tunnels.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(tunnels));
        redstone = redstone.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(redstone));
    }

    public RoomGeometry geometry() {
        return new RoomGeometry(gridIndex, size);
    }

    public Room withHost(Optional<GlobalPos> host) {
        return new Room(id, gridIndex, size, host, tunnels, redstone, entry);
    }

    public Room withTunnels(Map<Direction, BlockPos> tunnels) {
        return new Room(id, gridIndex, size, host, tunnels, redstone, entry);
    }

    public Room withRedstone(Map<Direction, RedstoneTunnel> redstone) {
        return new Room(id, gridIndex, size, host, tunnels, redstone, entry);
    }

    public Room withEntry(Optional<EntryPoint> entry) {
        return new Room(id, gridIndex, size, host, tunnels, redstone, entry);
    }

    /** True if the face has a tunnel of either kind. */
    public boolean isFaceUsed(Direction face) {
        return tunnels.containsKey(face) || redstone.containsKey(face);
    }

    /**
     * The next outer face without a tunnel, going round {@link Direction#values()} starting after
     * {@code after} (or from the first face when null). Null if every other face is taken.
     */
    public @Nullable Direction nextFreeFace(@Nullable Direction after) {
        Direction[] faces = Direction.values();
        int start = after == null ? 0 : after.ordinal() + 1;
        for (int i = 0; i < faces.length; i++) {
            Direction face = faces[(start + i) % faces.length];
            if (face != after && !isFaceUsed(face)) return face;
        }
        return null;
    }

    /** The face whose item/fluid/energy tunnel sits at {@code pos}, or null. */
    public @Nullable Direction faceAt(BlockPos pos) {
        for (Map.Entry<Direction, BlockPos> entry : tunnels.entrySet()) {
            if (entry.getValue().equals(pos)) return entry.getKey();
        }
        return null;
    }

    /** The face whose redstone tunnel sits at {@code pos}, or null. */
    public @Nullable Direction redstoneFaceAt(BlockPos pos) {
        for (Map.Entry<Direction, RedstoneTunnel> entry : redstone.entrySet()) {
            if (entry.getValue().pos().equals(pos)) return entry.getKey();
        }
        return null;
    }
}
