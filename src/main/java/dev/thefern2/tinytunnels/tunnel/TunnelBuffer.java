package dev.thefern2.tinytunnels.tunnel;

import java.util.ArrayList;
import java.util.List;

import dev.thefern2.tinytunnels.Config;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * What a buffered tunnel holds: a few item stacks and one fluid tank. Each end sees it through a
 * one-way view: the filling end can only insert, the draining end can only extract. Simulated calls
 * never change anything (the NeoForge handlers underneath guarantee it), which is what keeps this
 * safe without transactions.
 */
public final class TunnelBuffer {
    private final Runnable changed;
    private final ItemStackHandler items;
    private final FluidTank fluid;
    private final IItemHandler itemFill;
    private final IItemHandler itemDrain;
    private final IFluidHandler fluidFill;
    private final IFluidHandler fluidDrain;

    TunnelBuffer(Runnable changed) {
        this.changed = changed;
        this.items = new ItemStackHandler(Config.bufferItemSlots()) {
            @Override
            protected void onContentsChanged(int slot) {
                TunnelBuffer.this.changed.run();
            }
        };
        this.fluid = new FluidTank(Config.bufferFluidCapacity()) {
            @Override
            protected void onContentsChanged() {
                TunnelBuffer.this.changed.run();
            }
        };
        this.itemFill = new ItemView(items, true);
        this.itemDrain = new ItemView(items, false);
        this.fluidFill = new FluidView(fluid, true);
        this.fluidDrain = new FluidView(fluid, false);
    }

    IItemHandler items(boolean fill) {
        return fill ? itemFill : itemDrain;
    }

    IFluidHandler fluid(boolean fill) {
        return fill ? fluidFill : fluidDrain;
    }

    /** Both ways at once, for a player's bucket; pipes only ever get the one-way views. */
    IFluidHandler tank() {
        return fluid;
    }

    /** Throws the fluid away; only for an explicit, confirmed player action. */
    void discardFluid() {
        fluid.setFluid(FluidStack.EMPTY);
        changed.run();
    }

    public boolean isEmpty() {
        return fluid.isEmpty() && contents().isEmpty();
    }

    public FluidStack fluidContents() {
        return fluid.getFluid();
    }

    /** A copy of every non-empty stack. */
    public List<ItemStack> contents() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) stacks.add(stack.copy());
        }
        return stacks;
    }

    /** Removes and returns every stack, for dropping when the tunnel is removed. */
    List<ItemStack> takeItems() {
        List<ItemStack> taken = new ArrayList<>();
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.extractItem(slot, Integer.MAX_VALUE, false);
            if (!stack.isEmpty()) taken.add(stack);
        }
        return taken;
    }

    /** Moves as many items as {@code target} accepts; returns true if anything moved. */
    boolean pushItems(IItemHandler target) {
        boolean moved = false;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack available = items.extractItem(slot, items.getSlotLimit(slot), true);
            if (available.isEmpty()) continue;
            int accepted = available.getCount() - ItemHandlerHelper.insertItem(target, available.copy(), true).getCount();
            if (accepted <= 0) continue;
            ItemStack taken = items.extractItem(slot, accepted, false);
            ItemStack left = ItemHandlerHelper.insertItem(target, taken, false);
            // The target took less than it promised: keep the rest here rather than lose it.
            if (!left.isEmpty()) items.insertItem(slot, left, false);
            moved |= left.getCount() < taken.getCount();
        }
        return moved;
    }

    /** Moves as much fluid as {@code target} accepts; returns true if anything moved. */
    boolean pushFluid(IFluidHandler target) {
        if (fluid.isEmpty()) return false;
        return !FluidUtil.tryFluidTransfer(target, fluid, fluid.getFluidAmount(), true).isEmpty();
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("items", items.serializeNBT(registries));
        tag.put("fluid", fluid.writeToNBT(registries, new CompoundTag()));
        return tag;
    }

    void load(HolderLookup.Provider registries, CompoundTag tag) {
        // The saved slot count wins, so lowering the config never deletes items.
        if (tag.contains("items")) items.deserializeNBT(registries, tag.getCompound("items"));
        if (tag.contains("fluid")) fluid.readFromNBT(registries, tag.getCompound("fluid"));
    }

    /** One end of the buffer: inserts only (the filling end) or extracts only (the draining end). */
    private record ItemView(ItemStackHandler items, boolean fill) implements IItemHandler {
        @Override
        public int getSlots() {
            return items.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return fill ? items.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return fill ? ItemStack.EMPTY : items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return fill && items.isItemValid(slot, stack);
        }
    }

    private record FluidView(FluidTank fluid, boolean fill) implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return fluid.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return fluid.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return fill && fluid.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return fill ? fluid.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return fill ? FluidStack.EMPTY : fluid.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return fill ? FluidStack.EMPTY : fluid.drain(maxDrain, action);
        }
    }
}
