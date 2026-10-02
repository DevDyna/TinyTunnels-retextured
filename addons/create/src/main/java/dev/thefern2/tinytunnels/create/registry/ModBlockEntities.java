package dev.thefern2.tinytunnels.create.registry;

import java.util.function.Supplier;

import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.kinetic.KineticTunnelBlockEntity;
import dev.thefern2.tinytunnels.create.port.KineticPortBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TinyTunnelsCreate.MODID);

    public static final Supplier<BlockEntityType<KineticTunnelBlockEntity>> KINETIC_TUNNEL = BLOCK_ENTITIES.register("kinetic_tunnel",
            () -> BlockEntityType.Builder.of(KineticTunnelBlockEntity::new, ModBlocks.KINETIC_TUNNEL_WALL.get()).build(null));

    public static final Supplier<BlockEntityType<KineticPortBlockEntity>> KINETIC_PORT = BLOCK_ENTITIES.register("kinetic_port",
            () -> BlockEntityType.Builder.of(KineticPortBlockEntity::new, ModBlocks.KINETIC_PORT.get()).build(null));

    private ModBlockEntities() {}
}
