package dev.thefern2.tinytunnels.registry;

import java.util.EnumMap;
import java.util.Map;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineItem;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.teleport.ShrinkerItem;
import dev.thefern2.tinytunnels.tunnel.TunnelItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TinyTunnels.MODID);

    public static final Map<MachineSize, DeferredItem<MachineItem>> MACHINES = new EnumMap<>(MachineSize.class);

    static {
        ModBlocks.MACHINES.forEach((size, block) -> MACHINES.put(size,
                ITEMS.registerItem(size.blockId(), p -> new MachineItem(block.get(), p), Item.Properties::useBlockDescriptionPrefix)));
    }

    // The tunnel wall has no item of its own; the tunnel item places it.
    public static final DeferredItem<BlockItem> ROOM_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.ROOM_WALL);
    public static final DeferredItem<ShrinkerItem> SHRINKER = ITEMS.registerItem("shrinker", ShrinkerItem::new, p -> p.stacksTo(1));
    public static final DeferredItem<TunnelItem> TUNNEL = ITEMS.registerItem("tunnel", TunnelItem::new);

    private ModItems() {}
}
