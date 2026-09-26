package dev.thefern2.tinytunnels.room;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/** The void dimension that holds every room. Defined by datapack JSON under {@code data/tinytunnels/dimension}. */
public final class RoomDimension {
    public static final ResourceKey<Level> ROOM_DIM = ResourceKey.create(Registries.DIMENSION, TinyTunnels.id("rooms"));

    public static @Nullable ServerLevel getRoomLevel(MinecraftServer server) {
        return server.getLevel(ROOM_DIM);
    }

    public static boolean isRoomLevel(Level level) {
        return level.dimension() == ROOM_DIM;
    }

    private RoomDimension() {}
}
