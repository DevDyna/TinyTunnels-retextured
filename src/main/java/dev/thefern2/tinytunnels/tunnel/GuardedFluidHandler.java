package dev.thefern2.tinytunnels.tunnel;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Fluid version of {@link GuardedItemHandler}: pure passthrough, stores nothing. */
final class GuardedFluidHandler implements IFluidHandler {
    private final IFluidHandler delegate;

    GuardedFluidHandler(IFluidHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!ProxyGuard.enter()) return 0;
        try {
            return delegate.fill(resource, action);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (!ProxyGuard.enter()) return FluidStack.EMPTY;
        try {
            return delegate.drain(resource, action);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        if (!ProxyGuard.enter()) return FluidStack.EMPTY;
        try {
            return delegate.drain(maxDrain, action);
        } finally {
            ProxyGuard.exit();
        }
    }
}
