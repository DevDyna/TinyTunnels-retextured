package dev.thefern2.tinytunnels.create.kinetic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * What the two ends of each kinetic tunnel tell each other, one entry per room and wall position, so a
 * tunnel placed somewhere else never reads what the old one left. Transient: never saved, rebuilt within
 * a tick of both ends ticking. See {@link LinkedKineticBlockEntity} for who writes what.
 *
 * <p>Also the loop guard (A7.2, design in {@code tiny-tunnels-kinetic-tunnel.md}, "Why rotation can't loop"): once
 * per server tick, after every end has published its Create network, {@link #decide} works out which links may
 * drive. A link may drive only if the network it draws from is fed by a real source (a <b>root</b>), and none of
 * those roots already reaches the network it would drive or anything downstream of it. That stops loops (no free
 * energy, nothing counted twice) and diamonds (two links from the same source into one network).
 */
public final class KineticLinks {
    /** A reading older than this many ticks counts as nothing: the other end isn't ticking. */
    private static final int STALE_TICKS = 2;

    /** A Create network: its id is only unique within one level. */
    record Net(ResourceKey<Level> level, long id) {}

    static final class State {
        // Published by the consumer end (on the network the rotation comes from).
        float speed;
        float available;
        boolean overstressed;
        long consumerTick = Long.MIN_VALUE / 2;
        @Nullable Net consumerNet;
        boolean consumerRoot;
        // Published by the driving end: its share of the load on its network, and where it is.
        float demand;
        long sourceTick = Long.MIN_VALUE / 2;
        @Nullable Net driverNet;
        boolean driverRoot;
        // Decided by the loop guard each tick.
        boolean allowed;
        /** The last three passes' {@link #allowed}, newest in bit 0. */
        int history;
        /**
         * What the driving end obeys: allowed in this pass and in at least 2 of the last 3. Stopping is at once;
         * starting waits, because a pass can mix readings from before and after a change (a motor removed after some
         * ends published): a loop link could then see its driving network "not turning yet" and restart the loop for
         * a moment. A window rather than strictly consecutive passes, so one missed reading under server load doesn't
         * keep a link from ever starting.
         */
        boolean mayDrive;
        /**
         * Not allowed because the sources overlap (a loop, or a second link from the same source), not merely
         * because nothing powers it. Only this shows as "Blocked: same source on both sides".
         */
        boolean sameSource;

        boolean consumerFresh(long now) {
            return now - consumerTick <= STALE_TICKS;
        }

        boolean sourceFresh(long now) {
            return now - sourceTick <= STALE_TICKS;
        }
    }

    private record Key(UUID room, BlockPos wall) {}

    /** The fixed order the guard visits links in, so a diamond always keeps the same link. */
    private static final Comparator<Key> ORDER = Comparator.comparing(Key::room).thenComparingLong(key -> key.wall().asLong());

    /**
     * Links allowed on the last pass go first: in a diamond the link already driving keeps the line, and a newcomer
     * (a port placed later, or a tunnel flipped to drive the same line) is the one blocked. If the newcomer took over,
     * the line would switch sources at once, and two sources turning it opposite ways for a tick makes Create break
     * one of them (seen in game: the new port broke as soon as it was placed).
     */
    private static final Comparator<Map.Entry<Key, State>> VISIT = Comparator
            .comparing((Map.Entry<Key, State> entry) -> (entry.getValue().history & 1) == 0)
            .thenComparing(Map.Entry.comparingByKey(ORDER));

    private static final Map<Key, State> LINKS = new HashMap<>();

    static State get(UUID room, BlockPos wall) {
        return LINKS.computeIfAbsent(new Key(room, wall.immutable()), key -> new State());
    }

    /**
     * Converts a speed at one end to the other. A Create speed is absolute along its axis, and the
     * tunnel acts like one shaft that goes into the wall ({@code inward.getOpposite()}) and comes out of
     * the machine face, keeping its spin around the way it travels. Symmetric, so it works both ways.
     */
    public static float speedFactor(Direction inward, Direction face) {
        return -inward.getAxisDirection().getStep() * face.getAxisDirection().getStep();
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> LINKS.clear());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> decide(event.getServer().getTickCount()));
    }

    /**
     * The loop guard. Nodes are Create networks, each link an edge from its consumer's network to its driving end's.
     * A root network starts with the root set {itself}. Then, until nothing changes, in {@link #ORDER}: a link is
     * allowed if its consumer's root set {@code Rc} isn't empty and either its driving end has no network yet, or
     * {@code Rc} shares no root with the driving network or anything already downstream of it; allowing it adds
     * {@code Rc} to all of those. Root sets only grow, so this ends; the result doesn't depend on the order except
     * for which link of a diamond is kept.
     */
    static void decide(long now) {
        if (LINKS.isEmpty()) return;
        List<Map.Entry<Key, State>> links = new ArrayList<>();
        Map<Net, Set<Net>> roots = new HashMap<>();
        for (Map.Entry<Key, State> entry : LINKS.entrySet()) {
            State state = entry.getValue();
            state.allowed = false;
            // Readings from this tick or the one before: one missed publish under load doesn't drop the edge, and
            // anything older may be from before a change (the 2-tick freshness is for speed and stress only).
            if (!recent(state.consumerTick, now) || state.consumerNet == null) continue;
            links.add(entry);
            if (state.consumerRoot) roots.computeIfAbsent(state.consumerNet, net -> new HashSet<>()).add(state.consumerNet);
            Net driver = driverNet(state, now);
            if (driver != null && state.driverRoot) roots.computeIfAbsent(driver, net -> new HashSet<>()).add(driver);
        }
        links.sort(VISIT);

        // Allowed edges by the network they leave from, for walking downstream.
        Map<Net, List<Net>> downstream = new HashMap<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<Key, State> entry : links) {
                State state = entry.getValue();
                if (state.allowed) continue;
                Set<Net> rc = roots.getOrDefault(state.consumerNet, Set.of());
                if (rc.isEmpty()) continue;
                Net driver = driverNet(state, now);
                if (driver == null) {
                    // Not turning yet: nothing downstream to clash with. Checked properly once it has a network.
                    state.allowed = true;
                    changed = true;
                    continue;
                }
                Set<Net> reach = reachable(driver, downstream);
                if (reach.stream().anyMatch(net -> !Collections.disjoint(rc, roots.getOrDefault(net, Set.of())))) continue;
                state.allowed = true;
                changed = true;
                downstream.computeIfAbsent(state.consumerNet, net -> new ArrayList<>()).add(driver);
                Set<Net> add = Set.copyOf(rc);
                for (Net net : reach) roots.computeIfAbsent(net, n -> new HashSet<>()).addAll(add);
            }
        }
        for (State state : LINKS.values()) {
            state.history = ((state.history << 1) | (state.allowed ? 1 : 0)) & 0b111;
            state.mayDrive = state.allowed && Integer.bitCount(state.history) >= 2;
            state.sameSource = false;
        }
        // A link left out with a powered consumer was left out for overlapping sources: with no driving network it
        // would have been allowed. One with an unpowered consumer just has nothing to carry.
        for (Map.Entry<Key, State> entry : links) {
            State state = entry.getValue();
            if (!state.allowed && !roots.getOrDefault(state.consumerNet, Set.of()).isEmpty()) state.sameSource = true;
        }
    }

    /** The driving end's network, if that end published it this tick or the one before. */
    private static @Nullable Net driverNet(State state, long now) {
        return recent(state.sourceTick, now) ? state.driverNet : null;
    }

    private static boolean recent(long tick, long now) {
        return now - tick <= 1;
    }

    /** {@code from} and every network reached from it over allowed links. */
    private static Set<Net> reachable(Net from, Map<Net, List<Net>> downstream) {
        Set<Net> seen = new HashSet<>();
        Deque<Net> todo = new ArrayDeque<>();
        todo.add(from);
        while (!todo.isEmpty()) {
            Net net = todo.poll();
            if (!seen.add(net)) continue;
            todo.addAll(downstream.getOrDefault(net, List.of()));
        }
        return seen;
    }

    /**
     * Forgets a link's start history: its driving end changed (a tunnel flipped between IN and OUT, or a new port), so
     * it has to earn its start window again instead of driving on the old end's record.
     */
    static void restart(UUID room, BlockPos wall) {
        State state = LINKS.get(new Key(room, wall));
        if (state == null) return;
        state.history = 0;
        state.allowed = false;
        state.mayDrive = false;
    }

    private KineticLinks() {}
}
