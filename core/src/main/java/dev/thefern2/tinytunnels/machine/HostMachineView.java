package dev.thefern2.tinytunnels.machine;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.MachineView;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.room.Room;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** {@link MachineView} over a {@link MachineHost}, the public face of core's machine block entities. */
public record HostMachineView(MachineHost host) implements MachineView {
    @Override
    public BlockPos pos() {
        return host.getBlockPos();
    }

    @Override
    public Level level() {
        return Objects.requireNonNull(host.getLevel(), "a placed machine has a level");
    }

    @Override
    public @Nullable UUID roomId() {
        return host.getRoomId();
    }

    @Override
    public Optional<RoomView> hostedRoom() {
        return host.hostedRoom().map(Room::view);
    }
}
