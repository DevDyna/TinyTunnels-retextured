package dev.thefern2.tinytunnels.api;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** A placed machine block, from {@link TunnelService#machineAt}. Read it right away; don't keep it. */
public interface MachineView {
    BlockPos pos();

    Level level();

    /** The room bound to this machine, or null before it's bound. */
    @Nullable UUID roomId();

    /**
     * The room, only while this machine is its current host. Empty for a stale or duplicated machine, so use this,
     * not {@link #roomId()}, to find the tunnels a machine face leads to.
     */
    Optional<RoomView> hostedRoom();
}
