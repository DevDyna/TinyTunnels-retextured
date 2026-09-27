package dev.thefern2.tinytunnels.registry;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineBlockEntity;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelBlockEntity;
import dev.thefern2.tinytunnels.tunnel.TunnelBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TinyTunnels.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MachineBlockEntity>> MACHINE = BLOCK_ENTITIES.register("machine",
            () -> new BlockEntityType<>(MachineBlockEntity::new, ModBlocks.MACHINES.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new)));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TunnelBlockEntity>> TUNNEL = BLOCK_ENTITIES.register("tunnel",
            () -> new BlockEntityType<>(TunnelBlockEntity::new, ModBlocks.TUNNEL_WALL.get()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneTunnelBlockEntity>> REDSTONE_TUNNEL = BLOCK_ENTITIES.register("redstone_tunnel",
            () -> new BlockEntityType<>(RedstoneTunnelBlockEntity::new, ModBlocks.REDSTONE_TUNNEL_WALL.get()));

    private ModBlockEntities() {}
}
