package dev.thefern2.tinytunnels.tunnel;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;

/**
 * Stops tunnels from recursing forever: machine -> tunnel -> machine chains, nested rooms, and
 * pipe networks that loop back through a machine. Every proxied lookup and every proxied
 * insert/extract counts one level; past {@link #MAX_DEPTH} the proxy acts empty.
 */
public final class ProxyGuard {
    public static final int MAX_DEPTH = 8;

    private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
    private static boolean warned;

    /** Enters one level. Returns false (without entering) when already at the limit. */
    static boolean enter() {
        int[] depth = DEPTH.get();
        if (depth[0] >= MAX_DEPTH) {
            if (!warned) {
                warned = true;
                TinyTunnels.LOGGER.warn("Tunnel proxy depth limit ({}) reached; a tunnel loop or deep nesting is being cut off. Logged once.", MAX_DEPTH);
            }
            return false;
        }
        depth[0]++;
        return true;
    }

    static void exit() {
        DEPTH.get()[0]--;
    }

    /**
     * Resolves a proxied capability. Returns null only when the lookup itself says there is no
     * tunnel; past the depth limit returns the kind's empty handler. Real handlers come back
     * wrapped so their transfers are guarded too.
     */
    static <T> @Nullable T lookup(TransferKind<T> kind, Supplier<@Nullable T> resolve) {
        if (!enter()) return kind.empty();
        try {
            T handler = resolve.get();
            if (handler == null || handler == kind.empty()) return handler;
            return kind.guard().apply(handler);
        } finally {
            exit();
        }
    }

    private ProxyGuard() {}
}
