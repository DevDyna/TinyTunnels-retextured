package dev.thefern2.tinytunnels.tunnel;

import net.neoforged.neoforge.transfer.energy.DelegatingEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Energy version of {@link GuardedResourceHandler}: pure passthrough, stores nothing. */
final class GuardedEnergyHandler extends DelegatingEnergyHandler {
    GuardedEnergyHandler(EnergyHandler delegate) {
        super(() -> delegate);
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return super.insert(amount, transaction);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return super.extract(amount, transaction);
        } finally {
            ProxyGuard.exit();
        }
    }
}
