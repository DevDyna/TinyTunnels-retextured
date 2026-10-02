package dev.thefern2.tinytunnels.compat.create;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One end of a kinetic tunnel: the wall inside the room, or the machine outside. The ends are in
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
abstract class LinkedKineticBlockEntity extends GeneratingKineticBlockEntity {
    /** How this end is linked: which room and tunnel wall, whether it's the driving end, and the speed conversion to it. */
    protected record Link(UUID room, BlockPos wall, boolean driving, float speedFactor) {}

    private static final float EPSILON = 1e-3f;

    private float generatedSpeed;
    private float generatedCapacity;
    private float impact;
    private boolean driving;
    // Push the first values to Create even if they're 0: a loaded block may have saved a speed.
    private boolean synced;

    protected LinkedKineticBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** This end's link now, or null when it isn't linked (no tunnel, no host, a stale machine). */
    protected abstract @Nullable Link link();

    @Override
    public void tick() {
        super.tick();
        if (!(level instanceof ServerLevel server)) return;
        Link link = link();
        boolean nowDriving = link != null && link.driving();
        if (nowDriving != driving) {
            // A source can't turn into a consumer in place (or back): leave and rejoin in the new role.
            driving = nowDriving;
            generatedSpeed = 0;
            generatedCapacity = 0;
            impact = 0;
            leaveNetwork(!driving);
            return;
        }
        if (link == null) {
            setGenerated(0, 0);
            setImpact(0);
            return;
        }
        KineticLinks.State state = KineticLinks.get(link.room(), link.wall());
        long now = server.getServer().getTickCount();
        if (driving) {
            boolean fresh = state.consumerFresh(now);
            setGenerated(fresh ? state.speed * link.speedFactor() : 0, fresh && !state.overstressed ? state.available : 0);
            // A source claims no stress itself, so its network's stress is all load.
            state.demand = Math.max(0, stress);
            state.sourceTick = now;
        } else {
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
     * Leaves the network while the old state still holds, so the blocks this one turned see it go. The
     * same steps as Create's {@code switchToBlockState}, which only does them for {@code KineticBlock}s.
     * A source rejoins through {@code reActivateSource}; a consumer through {@code updateSpeed}.
     */
    void leaveNetwork() {
        generatedSpeed = 0;
        generatedCapacity = 0;
        leaveNetwork(!driving);
    }

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
     * clocks on gearshifts. A tunnel end's speed follows the other side, so its changes are deliberate,
     * and breaking the end would pop a machine off as an item.
     */
    @Override
    public int getFlickerScore() {
        return 0;
    }

    // TODO(debug): temporary, for the flaky kinetic_machine_replaced GameTest.
    public String debugLink() {
        Link link = level instanceof ServerLevel ? link() : null;
        String state = "";
        if (link != null && level instanceof ServerLevel server) {
            KineticLinks.State s = KineticLinks.get(link.room(), link.wall());
            long now = server.getServer().getTickCount();
            state = " state[speed " + s.speed + ", consumerAge " + (now - s.consumerTick) + ", sourceAge " + (now - s.sourceTick) + "]";
        }
        return "link " + link + ", driving " + driving + ", generatedSpeed " + generatedSpeed + ", synced " + synced + state;
    }

    /**
     * For Jade: the stress going through the tunnel, the same number at both ends. The consumer end claims
     * it; the driving end claims nothing (a source), so there it's the load on its own network.
     */
    public float passedStress() {
        return driving ? Math.max(0, stress) : claimedStress();
    }

    /** For tests: the stress this end claims for the load behind the other end, in SU. */
    public float claimedStress() {
        return impact * Math.abs(getTheoreticalSpeed());
    }
}
