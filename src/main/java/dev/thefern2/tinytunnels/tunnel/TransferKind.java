package dev.thefern2.tinytunnels.tunnel;

import java.util.List;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EmptyEnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.EmptyItemHandler;

/**
 * One kind of thing a tunnel carries: the block capability, what to hand out when the far side
 * has nothing (so pipes still connect), and how to wrap a real handler in the recursion guard.
 */
public record TransferKind<T>(String name, BlockCapability<T, @Nullable Direction> capability, T empty, UnaryOperator<T> guard) {
    public static final TransferKind<IItemHandler> ITEM = new TransferKind<>("item",
            Capabilities.ItemHandler.BLOCK, EmptyItemHandler.INSTANCE, GuardedItemHandler::new);
    public static final TransferKind<IFluidHandler> FLUID = new TransferKind<>("fluid",
            Capabilities.FluidHandler.BLOCK, EmptyFluidHandler.INSTANCE, GuardedFluidHandler::new);
    public static final TransferKind<IEnergyStorage> ENERGY = new TransferKind<>("energy",
            Capabilities.EnergyStorage.BLOCK, EmptyEnergyStorage.INSTANCE, GuardedEnergyStorage::new);

    public static final List<TransferKind<?>> ALL = List.of(ITEM, FLUID, ENERGY);
}
