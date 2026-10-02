package dev.thefern2.tinytunnels.machine;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.tunnel.TransferKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * A block entity that hosts a room: {@link MachineBlockEntity}, or a compat block entity that has to
 * extend another mod's class (e.g. a Create kinetic machine). Code outside the machine package checks
 * {@code instanceof MachineHost}, never a concrete block entity class. The machine logic lives in
 * {@link MachineCore}; implementors forward their block entity hooks to it.
 */
public interface MachineHost {
    MachineCore core();

    // Implemented by BlockEntity.
    BlockPos getBlockPos();

    @Nullable Level getLevel();

    boolean isRemoved();

    default @Nullable UUID getRoomId() {
        return core().getRoomId();
    }

    default Optional<Room> getRoom() {
        return core().getRoom();
    }

    /** The room, only while this machine is its current host (not a stale or duplicated machine). */
    default Optional<Room> hostedRoom() {
        return core().hostedRoom();
    }

    default void bindOnPlace(ServerLevel level, MachineSize size) {
        core().bindOnPlace(level, size);
    }

    default <T> @Nullable T insideCapability(TransferKind<T> kind, Direction face) {
        return core().insideCapability(kind, face);
    }
}
