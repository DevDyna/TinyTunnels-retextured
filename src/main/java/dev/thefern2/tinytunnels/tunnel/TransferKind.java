package dev.thefern2.tinytunnels.tunnel;

import java.util.List;
import java.util.function.UnaryOperator;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.EmptyResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EmptyEnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * One kind of thing a tunnel carries: the block capability, what to hand out when the far side
 * has nothing (so pipes still connect), and how to wrap a real handler in the recursion guard.
 */
public record TransferKind<T>(String name, BlockCapability<T, @Nullable Direction> capability, T empty, UnaryOperator<T> guard) {
    public static final TransferKind<ResourceHandler<ItemResource>> ITEM = new TransferKind<>("item",
            Capabilities.Item.BLOCK, EmptyResourceHandler.instance(), GuardedResourceHandler::new);
    public static final TransferKind<ResourceHandler<FluidResource>> FLUID = new TransferKind<>("fluid",
            Capabilities.Fluid.BLOCK, EmptyResourceHandler.instance(), GuardedResourceHandler::new);
    public static final TransferKind<EnergyHandler> ENERGY = new TransferKind<>("energy",
            Capabilities.Energy.BLOCK, EmptyEnergyHandler.INSTANCE, GuardedEnergyHandler::new);

    public static final List<TransferKind<?>> ALL = List.of(ITEM, FLUID, ENERGY);
}
