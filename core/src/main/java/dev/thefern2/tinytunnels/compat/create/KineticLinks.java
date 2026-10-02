package dev.thefern2.tinytunnels.compat.create;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * What the two ends of each kinetic tunnel tell each other, one entry per room and wall position, so a
 * tunnel placed somewhere else never reads what the old one left. Transient: never saved, rebuilt within
 * a tick of both ends ticking. See
 * {@link LinkedKineticBlockEntity} for who writes what.
 */
final class KineticLinks {
    /** A reading older than this many ticks counts as nothing: the other end isn't ticking. */
    private static final int STALE_TICKS = 2;

    static final class State {
        // Published by the consumer end (on the network the rotation comes from).
        float speed;
        float available;
        boolean overstressed;
        long consumerTick = Long.MIN_VALUE / 2;
        // Published by the driving end: the load on its network.
        float demand;
        long sourceTick = Long.MIN_VALUE / 2;

        boolean consumerFresh(long now) {
            return now - consumerTick <= STALE_TICKS;
        }

        boolean sourceFresh(long now) {
            return now - sourceTick <= STALE_TICKS;
        }
    }

    private record Key(UUID room, BlockPos wall) {}

    private static final Map<Key, State> LINKS = new HashMap<>();

    static State get(UUID room, BlockPos wall) {
        return LINKS.computeIfAbsent(new Key(room, wall.immutable()), key -> new State());
    }

    /**
     * Converts a speed at one end to the other. A Create speed is absolute along its axis, and the
     * tunnel acts like one shaft that goes into the wall ({@code inward.getOpposite()}) and comes out of
     * the machine face, keeping its spin around the way it travels. Symmetric, so it works both ways.
     */
    static float speedFactor(Direction inward, Direction face) {
        return -inward.getAxisDirection().getStep() * face.getAxisDirection().getStep();
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> LINKS.clear());
    }

    private KineticLinks() {}
}
