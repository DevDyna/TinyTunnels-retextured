package dev.thefern2.tinytunnels.registry;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineSize;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TinyTunnels.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tinytunnels"))
            .withTabsBefore(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .icon(() -> ModItems.MACHINES.get(MachineSize.NORMAL).get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                ModItems.MACHINES.values().forEach(output::accept);
                output.accept(ModItems.SHRINKER);
                output.accept(ModItems.TUNNEL);
                output.accept(ModItems.REDSTONE_TUNNEL);
                output.accept(ModItems.TUNNEL_WRENCH);
                output.accept(ModItems.ROOM_WALL);
            })
            .build());

    private ModCreativeTabs() {}
}
