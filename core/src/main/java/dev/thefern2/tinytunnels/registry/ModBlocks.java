package dev.thefern2.tinytunnels.registry;

import java.util.EnumMap;
import java.util.Map;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.compat.Compat;
import dev.thefern2.tinytunnels.compat.create.CreateBlocks;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.TunnelWallBlock;
import dev.thefern2.tinytunnels.wall.RoomWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TinyTunnels.MODID);

    // With Create, each machine is a KineticMachineBlock under the same id, so it can carry a shaft.
    public static final Map<MachineSize, DeferredBlock<MachineBlock>> MACHINES = new EnumMap<>(MachineSize.class);

    static {
        for (MachineSize size : MachineSize.values()) {
            MACHINES.put(size, BLOCKS.registerBlock(size.blockId(), p -> Compat.CREATE ? CreateBlocks.machine(size, p) : new MachineBlock(size, p), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .sound(SoundType.METAL)
                    // Blast-proof: losing the machine item would strand its room.
                    .strength(4f, 1200f)
                    .requiresCorrectToolForDrops()
                    .isRedstoneConductor(ModBlocks::never)));
        }
    }

    // Room walls are unbreakable, drop nothing and can't be pushed; the tunnel wall replaces one of them.
    public static final DeferredBlock<RoomWallBlock> ROOM_WALL = BLOCKS.registerBlock("room_wall", RoomWallBlock::new, wallProperties());
    public static final DeferredBlock<TunnelWallBlock> TUNNEL_WALL = BLOCKS.registerBlock("tunnel_wall", TunnelWallBlock::new, wallProperties());
    public static final DeferredBlock<RedstoneTunnelWallBlock> REDSTONE_TUNNEL_WALL = BLOCKS.registerBlock("redstone_tunnel_wall", RedstoneTunnelWallBlock::new, wallProperties());
    // Always registered, so removing Create leaves an inert wall instead of a hole; with Create it has a shaft.
    public static final DeferredBlock<KineticTunnelWallBlock> KINETIC_TUNNEL_WALL = BLOCKS.registerBlock("kinetic_tunnel_wall",
            p -> Compat.CREATE ? CreateBlocks.kineticTunnelWall(p) : new KineticTunnelWallBlock(p), wallProperties());

    private static BlockBehaviour.Properties wallProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
                .sound(SoundType.METAL)
                .strength(-1f, 3_600_000f)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK)
                .isValidSpawn((state, level, pos, type) -> false)
                .lightLevel(state -> 12)
                .isRedstoneConductor(ModBlocks::never);
    }

    // Not redstone conductors: chests open underneath, and power doesn't leak through walls or machines by accident.
    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }

    private ModBlocks() {}
}
