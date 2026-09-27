package dev.thefern2.tinytunnels.tunnel;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Passes everything straight to the handler on the other side of a tunnel, caller's
 * {@code simulate} flag included. Holds no items; only counts recursion around insert/extract.
 */
final class GuardedItemHandler implements IItemHandler {
    private final IItemHandler delegate;

    GuardedItemHandler(IItemHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public int getSlots() {
        return delegate.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return delegate.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!ProxyGuard.enter()) return stack;
        try {
            return delegate.insertItem(slot, stack, simulate);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!ProxyGuard.enter()) return ItemStack.EMPTY;
        try {
            return delegate.extractItem(slot, amount, simulate);
        } finally {
            ProxyGuard.exit();
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return delegate.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return delegate.isItemValid(slot, stack);
    }
}
