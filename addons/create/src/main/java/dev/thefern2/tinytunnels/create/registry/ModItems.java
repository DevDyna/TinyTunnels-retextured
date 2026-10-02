package dev.thefern2.tinytunnels.create.registry;

import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelItem;
import net.minecraft.world.item.BlockItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TinyTunnelsCreate.MODID);

    public static final DeferredItem<KineticTunnelItem> KINETIC_TUNNEL = ITEMS.registerItem("kinetic_tunnel", KineticTunnelItem::new);
    public static final DeferredItem<BlockItem> KINETIC_PORT = ITEMS.registerSimpleBlockItem(ModBlocks.KINETIC_PORT);

    private ModItems() {}
}
