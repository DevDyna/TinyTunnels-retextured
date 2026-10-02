package dev.thefern2.tinytunnels.registry;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.compat.Compat;
import dev.thefern2.tinytunnels.compat.create.CreateBlocks;
import dev.thefern2.tinytunnels.machine.MachineBlockEntity;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelBlockEntity;
import dev.thefern2.tinytunnels.tunnel.TunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TinyTunnels.MODID);

    /**
     * With Create this builds the kinetic machine too, so machines placed before Create was added (or
     * before the kinetic tunnel existed) become kinetic when their chunk loads. Both kinds save the room
     * under the same key.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> MACHINE = BLOCK_ENTITIES.register("machine",
            () -> BlockEntityType.Builder.of(ModBlockEntities::newMachine, machineBlocks()).build(null));

    /**
     * Always registered, with or without Create, so a machine saved under this id never loses its room:
     * a chunk drops a block entity whose id isn't registered. With Create it builds the kinetic machine,
     * without it a plain machine.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> KINETIC_MACHINE = BLOCK_ENTITIES.register("kinetic_machine",
            () -> BlockEntityType.Builder.of(ModBlockEntities::newKineticMachine, machineBlocks()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TunnelBlockEntity>> TUNNEL = BLOCK_ENTITIES.register("tunnel",
            () -> BlockEntityType.Builder.of(TunnelBlockEntity::new, ModBlocks.TUNNEL_WALL.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RedstoneTunnelBlockEntity>> REDSTONE_TUNNEL = BLOCK_ENTITIES.register("redstone_tunnel",
            () -> BlockEntityType.Builder.of(RedstoneTunnelBlockEntity::new, ModBlocks.REDSTONE_TUNNEL_WALL.get()).build(null));

    /** Create only: without Create the wall is inert and has no block entity. */
    public static final @Nullable DeferredHolder<BlockEntityType<?>, BlockEntityType<BlockEntity>> KINETIC_TUNNEL = Compat.CREATE
            ? BLOCK_ENTITIES.register("kinetic_tunnel", () -> BlockEntityType.Builder.of(ModBlockEntities::newKineticTunnel, ModBlocks.KINETIC_TUNNEL_WALL.get()).build(null))
            : null;

    private static BlockEntity newKineticTunnel(BlockPos pos, BlockState state) {
        return CreateBlocks.kineticTunnel(KINETIC_TUNNEL.get(), pos, state);
    }

    private static Block[] machineBlocks() {
        return ModBlocks.MACHINES.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new);
    }

    private static BlockEntity newMachine(BlockPos pos, BlockState state) {
        return Compat.CREATE ? CreateBlocks.kineticMachine(MACHINE.get(), pos, state) : new MachineBlockEntity(pos, state);
    }

    private static BlockEntity newKineticMachine(BlockPos pos, BlockState state) {
        return Compat.CREATE ? CreateBlocks.kineticMachine(KINETIC_MACHINE.get(), pos, state) : new MachineBlockEntity(KINETIC_MACHINE.get(), pos, state);
    }

    private ModBlockEntities() {}
}
