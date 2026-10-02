package dev.thefern2.tinytunnels.create.kinetic;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One end of a kinetic tunnel: the wall inside the room, or the port outside the machine. The ends are in
 * different levels, so they can't share a Create network. Each is its own kinetic block, and they swap
 * numbers through {@link KineticLinks} every tick.
 *
 * <ul>
 *   <li>The <b>consumer</b> end is on the network the rotation comes from. It publishes that network's
 *       speed, its spare capacity and whether it's overstressed, and claims the load behind the other end
 *       as its own stress impact, so the far load counts against the near network.</li>
 *   <li>The <b>driving</b> end is a source: it spins at the consumer's speed, offers its spare capacity
 *       (none while the consumer is overstressed), and publishes the load on its own network.</li>
 * </ul>
 *
 * Create charges stress at a block's theoretical speed, even overstressed, so an overload stops both sides
 * and they stay stopped until load is removed or capacity added, as Create itself behaves.
 */
public abstract class LinkedKineticBlockEntity extends GeneratingKineticBlockEntity {
    /** How this end is linked: which room and tunnel wall, whether it's the driving end, and the speed conversion to it. */
    public record Link(UUID room, BlockPos wall, boolean driving, float speedFactor) {}

    private static final float EPSILON = 1e-3f;
    /** Longer source chains than this count as a loop; Create's own networks are far shorter in practice. */
    private static final int MAX_SOURCE_CHAIN = 4096;
    private static final String LINKED_KEY = "TinyTunnelsLinked";
    private static final String BLOCKED_KEY = "TinyTunnelsBlocked";

    private float generatedSpeed;
    private float generatedCapacity;
    private float impact;
    private boolean driving;
    /** Server: whether the last tick found a link. Client: as last synced, for the goggles. */
    private boolean linked;
    // Push the first values to Create even if they're 0: a loaded block may have saved a speed.
    private boolean synced;
    /** False until the first server tick after this block entity was created or loaded. */
    private boolean started;
    /**
     * Driving end only: the loop guard stopped its link on the last tick because the sources overlap (see
     * {@link KineticLinks#decide}). An unpowered link isn't blocked: it just has nothing to carry.
     */
    private boolean blocked;
    /** Driving end only: the share of its network's stress it passed back on the last tick, in SU. */
    private float demand;

    protected LinkedKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** This end's link now, or null when it isn't linked (no tunnel, no machine, a stale machine). */
    protected abstract @Nullable Link link();

    /** The tunnel direction for which this end drives its network: OUT for the port, IN for the wall. */
    protected abstract KineticMode drivingMode();

    @Override
    public void tick() {
        if (!started && level instanceof ServerLevel) {
            started = true;
            dropSavedSource();
        }
        super.tick();
        if (!(level instanceof ServerLevel server)) return;
        if (hasSource() && sourceLoopsBack()) {
            // Turned by nothing real: a Create source loop through this end (A6.8, A7.2). Drop out, which clears
            // the blocks whose source this end is. A driving end is set up again by the link; a consumer rejoins
            // whatever real network is next to it.
            dropSource();
            if (!driving) updateSpeed = true;
            return;
        }
        Link link = link();
        setLinked(link != null);
        boolean nowDriving = link != null && link.driving();
        if (nowDriving != driving) {
            // A source can't turn into a consumer in place (or back): leave and rejoin in the new role.
            driving = nowDriving;
            // A new driving end starts from nothing, not from the old one's record (KineticLinks#restart).
            if (driving) KineticLinks.restart(link.room(), link.wall());
            setBlocked(false);
            demand = 0;
            generatedSpeed = 0;
            generatedCapacity = 0;
            impact = 0;
            leaveNetwork(!driving);
            return;
        }
        if (link == null) {
            setBlocked(false);
            demand = 0;
            setGenerated(0, 0);
            setImpact(0);
            return;
        }
        KineticLinks.State state = KineticLinks.get(link.room(), link.wall());
        long now = server.getServer().getTickCount();
        if (driving) {
            state.driverNet = net();
            state.driverRoot = isRoot();
            state.sourceTick = now;
            setBlocked(!state.mayDrive && state.sameSource);
            if (!state.mayDrive) {
                // Not allowed (same source on both sides, unpowered, or waiting its start pass): turn nothing, offer
                // nothing, pass no load back.
                demand = 0;
                state.demand = 0;
                setGenerated(0, 0);
                return;
            }
            boolean fresh = state.consumerFresh(now);
            setGenerated(fresh ? state.speed * link.speedFactor() : 0, fresh && !state.overstressed ? state.available : 0);
            demand = Math.max(0, stress) * loadShare(state.available);
            state.demand = demand;
        } else {
            state.consumerNet = net();
            state.consumerRoot = isRoot();
            float speed = getTheoreticalSpeed();
            state.speed = speed;
            state.available = Math.max(0, capacity - (stress - impact * Math.abs(speed)));
            state.overstressed = isOverStressed();
            state.consumerTick = now;
            float demand = state.sourceFresh(now) ? state.demand : 0;
            setImpact(speed == 0 ? 0 : demand / Math.abs(speed));
        }
    }

    /**
     * An end saved while driving loads with its old speed and no source; the blocks it turned load with that speed
     * and this end as their source. Its speed is recomputed from the link, so it must not keep the saved one. If it
     * did, Create's first validation would zero it silently ({@code driving} starts false, so the generated speed is
     * 0), and leaving the network would then skip clearing the neighbours, which ignores a block at speed 0. On
     * rejoin, a neighbour still at the old speed would overpower this end and become its source, with this end as
     * the neighbour's source: Create then ignores every lower speed for a source with a source ("staying below
     * overpowered speed"), so the outside stayed at the old speed (A6.8). So: leave while the saved speed still
     * holds, which clears the neighbours, before Create's tick runs. The link sets the speed again next tick.
     * An end saved as a consumer has a source; Create's own loading handles it.
     */
    private void dropSavedSource() {
        if (hasSource() || speed == 0) return;
        dropSource();
    }

    /**
     * Leaves while the current speed still holds, so the blocks whose source this end is are cleared too. The
     * generated speed goes to 0 first: Create's missing-source pass must not pick this end as a source again.
     */
    private void dropSource() {
        generatedSpeed = 0;
        generatedCapacity = 0;
        detachKinetics();
        removeSource();
        reActivateSource = true;
    }

    /**
     * True when this end's chain of sources is a real loop: it comes back to this end, or goes round in a circle.
     * Checked every tick on every end, whatever its role and whether it's blocked. A driving end in such a loop
     * would ignore every lower speed from the link (A6.8); any end in one would keep a line turning with no
     * generator at all (A7.2, after a loop was cut). Any chain that ends, wherever it ends, is left alone: at a
     * motor or a driving tunnel end it's healthy, and a source that has stopped or gone is Create's to clear. An
     * end that's only waiting for its upstream to start must not be dropped (that stopped links starting under
     * load). A chain into an unloaded chunk is left alone too.
     */
    private boolean sourceLoopsBack() {
        Set<BlockPos> seen = new HashSet<>();
        BlockPos at = source;
        while (at != null) {
            if (at.equals(worldPosition) || !seen.add(at) || seen.size() > MAX_SOURCE_CHAIN) return true;
            if (!level.isLoaded(at)) return false;
            // A source that's gone is Create's to clear (validateKinetics); only a real loop is ours.
            if (!(level.getBlockEntity(at) instanceof KineticBlockEntity next)) return false;
            at = next.source;
        }
        return false;
    }

    /**
     * The part of this network's load this end passes back through the tunnel: its share of the network's
     * capacity. A source claims no stress itself, so the network's stress is all load. With no other source on the
     * network it's all of it, as before A7; with an outside motor or another room on the line, each pays its part.
     * This end's part is the capacity it could offer ({@code available} from the other side), not what it offers
     * right now, which drops to 0 while the other side is overstressed and would make the share flap.
     */
    private float loadShare(float available) {
        if (!hasNetwork()) return 1;
        KineticNetwork network = getOrCreateNetwork();
        float others = 0;
        for (KineticBlockEntity source : network.sources.keySet()) {
            if (source != this) others += network.getActualCapacityOf(source);
        }
        float own = Math.max(0, available);
        return own + others <= 0 ? 1 : own / (own + others);
    }

    /** The Create network this end is in, or null while it doesn't turn. */
    private KineticLinks.@Nullable Net net() {
        return network == null ? null : new KineticLinks.Net(level.dimension(), network);
    }

    /** True if this end's network has a real source: one that isn't a tunnel end. */
    private boolean isRoot() {
        if (!hasNetwork()) return false;
        for (KineticBlockEntity source : getOrCreateNetwork().sources.keySet()) {
            if (!(source instanceof LinkedKineticBlockEntity)) return true;
        }
        return false;
    }

    private void setBlocked(boolean value) {
        if (value == blocked) return;
        blocked = value;
        sendData();
    }

    private void setLinked(boolean value) {
        if (value == linked) return;
        linked = value;
        sendData();
    }

    /**
     * Leaves the network while the old state still holds, so the blocks this one turned see it go. The
     * same steps as Create's {@code switchToBlockState}, which only does them for {@code KineticBlock}s.
     * A source rejoins through {@code reActivateSource}; a consumer through {@code updateSpeed}.
     */
    private void leaveNetwork(boolean rejoinAsConsumer) {
        if (hasNetwork()) getOrCreateNetwork().remove(this);
        detachKinetics();
        removeSource();
        reActivateSource = true;
        if (rejoinAsConsumer) updateSpeed = true;
    }

    private void setGenerated(float speed, float capacity) {
        if (synced && close(speed, generatedSpeed) && close(capacity, generatedCapacity)) return;
        synced = true;
        generatedSpeed = speed;
        generatedCapacity = capacity;
        updateGeneratedRotation();
    }

    private void setImpact(float value) {
        if (close(value, impact)) return;
        impact = value;
        if (hasNetwork()) {
            KineticNetwork network = getOrCreateNetwork();
            network.updateStressFor(this, calculateStressApplied());
            network.updateStress();
        }
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) <= Math.max(EPSILON, Math.abs(b) * EPSILON);
    }

    @Override
    public float getGeneratedSpeed() {
        return driving ? generatedSpeed : 0;
    }

    /** Create counts capacity per RPM, so the total spare capacity is divided by the speed. */
    @Override
    public float calculateAddedStressCapacity() {
        float speed = Math.abs(getGeneratedSpeed());
        float added = speed == 0 ? 0 : generatedCapacity / speed;
        lastCapacityProvided = added;
        return added;
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = impact;
        return impact;
    }

    /**
     * Create breaks a kinetic block whose speed changes too often (its flicker score), to stop redstone
     * clocks on gearshifts. A tunnel end's speed follows the other side, so its changes are deliberate.
     */
    @Override
    public int getFlickerScore() {
        return 0;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (clientPacket) {
            tag.putBoolean(LINKED_KEY, linked);
            tag.putBoolean(BLOCKED_KEY, blocked);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            linked = tag.getBoolean(LINKED_KEY);
            blocked = tag.getBoolean(BLOCKED_KEY);
        }
    }

    /** Create's goggles: a line saying the end isn't linked, under Create's own lines. */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        String key = !linked ? "jade.tinytunnels_create.not_linked" : blocked ? "jade.tinytunnels_create.blocked_same_source" : null;
        if (key == null) return added;
        tooltip.add(Component.literal("    ").append(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
        return true;
    }

    /**
     * Whether the loop guard stops this end from driving because the network it would drive is already fed by the
     * same source (a loop, or a second tunnel from the same network). False for a link that's merely unpowered,
     * which shows as an idle link. Server: as of its last tick; client: as synced.
     */
    public boolean isBlocked() {
        return blocked;
    }

    /** Whether this end found its tunnel on its last tick (server), or as last synced (client). */
    public boolean isLinked() {
        return linked;
    }

    /** The tunnel's direction as this end sees it, or null when it isn't linked. Server only. */
    public @Nullable KineticMode mode() {
        if (!linked) return null;
        return driving ? drivingMode() : drivingMode().flip();
    }

    /**
     * For Jade: the stress going through the tunnel, the same number at both ends. The consumer end claims
     * it; the driving end claims nothing (a source), so there it's its share of the load on its own network.
     */
    public float passedStress() {
        return driving ? demand : claimedStress();
    }

    /** For tests: the stress this end claims for the load behind the other end, in SU. */
    public float claimedStress() {
        return impact * Math.abs(getTheoreticalSpeed());
    }
}
