package dev.thefern2.tinytunnels.tunnel;

import net.neoforged.neoforge.transfer.DelegatingResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Passes everything straight to the handler on the other side of a tunnel, caller's transaction
 * included. Holds no items or fluid; only counts recursion around insert/extract.
 */
final class GuardedResourceHandler<T extends Resource> extends DelegatingResourceHandler<T> {
    GuardedResourceHandler(ResourceHandler<T> delegate) {
        super(delegate);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return super.insert(index, resource, amount, transaction);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return super.insert(resource, amount, transaction);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return super.extract(index, resource, amount, transaction);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return super.extract(resource, amount, transaction);
        } finally {
            ProxyGuard.exit();
        }
    }
}
