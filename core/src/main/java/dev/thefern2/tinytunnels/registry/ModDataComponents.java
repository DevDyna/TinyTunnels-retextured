package dev.thefern2.tinytunnels.registry;

import java.util.UUID;

import dev.thefern2.tinytunnels.TinyTunnels;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, TinyTunnels.MODID);

    /** The room a machine item is bound to. Absent on a machine that has never been placed. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> ROOM_ID = COMPONENTS.registerComponentType("room_id",
            builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    private ModDataComponents() {}
}
