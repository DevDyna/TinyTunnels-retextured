package dev.thefern2.tinytunnels.room;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.server.loading.ServerModLoader;

/**
 * The void dimension that holds every room. Defined by datapack JSON under {@code data/tinytunnels/dimension}.
 *
 * <p>The GameTest server builds its world without datapack dimensions, so there (and only there)
 * rooms live in the overworld instead. Always resolve the level and its key through this class.
 */
public final class RoomDimension {
    public static final ResourceKey<Level> ROOM_DIM = ResourceKey.create(Registries.DIMENSION, TinyTunnels.id("rooms"));

    public static @Nullable ServerLevel getRoomLevel(MinecraftServer server) {
        ServerLevel rooms = server.getLevel(ROOM_DIM);
        if (rooms == null && ServerModLoader.isGameTestServer()) return server.overworld();
        return rooms;
    }

    /** The dimension rooms are actually in: {@link #ROOM_DIM}, except on the GameTest server. */
    public static ResourceKey<Level> key(MinecraftServer server) {
        ServerLevel rooms = getRoomLevel(server);
        return rooms == null ? ROOM_DIM : rooms.dimension();
    }

    public static boolean isRoomLevel(Level level) {
        if (level instanceof ServerLevel server) return server == getRoomLevel(server.getServer());
        return level.dimension() == ROOM_DIM;
    }

    private RoomDimension() {}
}
