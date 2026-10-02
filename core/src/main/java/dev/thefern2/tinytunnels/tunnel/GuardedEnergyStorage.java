package dev.thefern2.tinytunnels.tunnel;

import net.neoforged.neoforge.energy.IEnergyStorage;

/** Energy version of {@link GuardedItemHandler}: pure passthrough, stores nothing. */
final class GuardedEnergyStorage implements IEnergyStorage {
    private final IEnergyStorage delegate;

    GuardedEnergyStorage(IEnergyStorage delegate) {
        this.delegate = delegate;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return delegate.receiveEnergy(toReceive, simulate);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return delegate.extractEnergy(toExtract, simulate);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public int getEnergyStored() {
        return delegate.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return delegate.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return delegate.canExtract();
    }

    @Override
    public boolean canReceive() {
        return delegate.canReceive();
    }
}
