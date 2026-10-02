package dev.thefern2.tinytunnels.tunnel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

/**
 * The capability caches a machine or tunnel keeps for the blocks on the other side. One cache per
 * key (transfer kind, plus face for machines). When the far endpoint moves (tunnel cycled, host
 * moved) the old cache is retired and a new one made.
 */
public final class EndpointCaches {
    private record Endpoint(ServerLevel level, BlockPos pos, Direction side) {}

    private static final class Entry<T> {
        final Endpoint endpoint;
        final BlockCapabilityCache<T, @Nullable Direction> cache;
        boolean retired;

        Entry(TransferKind<T> kind, Endpoint endpoint, BooleanSupplier ownerValid, Runnable onChange) {
            this.endpoint = endpoint;
            this.cache = BlockCapabilityCache.create(kind.capability(), endpoint.level(), endpoint.pos(), endpoint.side(),
                    () -> !retired && ownerValid.getAsBoolean(), onChange);
        }
    }

    private final Map<Object, Entry<?>> entries = new HashMap<>();
    private final BooleanSupplier ownerValid;
    private final Runnable onChange;

    /**
     * @param ownerValid false once the owning block entity is gone, so caches stop listening
     * @param onChange   called when a cached endpoint's capability changes; must not query capabilities
     */
    public EndpointCaches(BooleanSupplier ownerValid, Runnable onChange) {
        this.ownerValid = ownerValid;
        this.onChange = onChange;
    }

    /**
     * The capability at the endpoint, or the kind's empty handler if that chunk isn't loaded or
     * exposes nothing. Never loads chunks.
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, TransferKind<T> kind, ServerLevel level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) return kind.empty();
        Endpoint endpoint = new Endpoint(level, pos.immutable(), side);
        Entry<T> entry = (Entry<T>) entries.get(key);
        if (entry == null || !entry.endpoint.equals(endpoint)) {
            if (entry != null) entry.retired = true;
            entry = new Entry<>(kind, endpoint, ownerValid, onChange);
            entries.put(key, entry);
        }
        T capability = entry.cache.getCapability();
        return capability == null ? kind.empty() : capability;
    }

    public void clear() {
        entries.values().forEach(entry -> entry.retired = true);
        entries.clear();
    }
}
