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
 * @param tunnels outer face of the machine -> tunnel wall position in the room dimension
 */
public record Room(UUID id, int gridIndex, int size, Optional<GlobalPos> host, Map<Direction, BlockPos> tunnels) {
    public static final Codec<Room> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Room::id),
            Codec.INT.fieldOf("grid_index").forGetter(Room::gridIndex),
            Codec.INT.fieldOf("size").forGetter(Room::size),
            GlobalPos.CODEC.optionalFieldOf("host").forGetter(Room::host),
            Codec.unboundedMap(Direction.CODEC, BlockPos.CODEC).optionalFieldOf("tunnels", Map.of()).forGetter(Room::tunnels)
    ).apply(i, Room::new));

    public Room {
        tunnels = tunnels.isEmpty() ? Map.of() : Map.copyOf(new EnumMap<>(tunnels));
    }

    public RoomGeometry geometry() {
        return new RoomGeometry(gridIndex, size);
    }

    public Room withHost(Optional<GlobalPos> host) {
        return new Room(id, gridIndex, size, host, tunnels);
    }

    public Room withTunnels(Map<Direction, BlockPos> tunnels) {
        return new Room(id, gridIndex, size, host, tunnels);
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
            if (face != after && !tunnels.containsKey(face)) return face;
        }
        return null;
    }

    /** The face whose tunnel sits at {@code pos}, or null. */
    public @Nullable Direction faceAt(BlockPos pos) {
        for (Map.Entry<Direction, BlockPos> entry : tunnels.entrySet()) {
            if (entry.getValue().equals(pos)) return entry.getKey();
        }
        return null;
    }
}
