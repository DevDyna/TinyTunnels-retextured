package dev.thefern2.tinytunnels.room;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * A saved tunnel whose kind isn't registered (its mod was removed), or whose data its kind can't read. The room
 * keeps the entry exactly as it was loaded and saves it back unchanged, so the tunnel comes back when the kind
 * does. Its face counts as used, and the shell repair puts the inert {@code tinytunnels:unknown_tunnel_wall} at
 * its wall so the shell has no hole. Nothing can move or remove it.
 *
 * @param kind  the kind's id as saved
 * @param wall  the tunnel wall's position
 * @param saved the whole saved entry ({@code kind}, {@code wall}, {@code data}), written back as is
 */
public record UnknownTunnel(ResourceLocation kind, BlockPos wall, Dynamic<?> saved) {
    public UnknownTunnel {
        wall = wall.immutable();
    }

    /** Reads the kind id and the wall of a saved entry, keeping the entry itself. */
    static <T> DataResult<UnknownTunnel> read(Dynamic<T> entry) {
        return entry.get("kind").asString().flatMap(ResourceLocation::read)
                .flatMap(kind -> entry.get("wall").get().flatMap(BlockPos.CODEC::parse)
                        .map(wall -> new UnknownTunnel(kind, wall, entry)));
    }
}
