package dev.thefern2.tinytunnels.compat.create;

import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineSize;
import dev.thefern2.tinytunnels.tunnel.KineticTunnelWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The entry points the rest of the mod calls, only when {@code Compat.CREATE} is set. Their declared
 * types are Create-free on purpose: the verifier may load a class just because a caller's method
 * returns it, so callers never see a Create type.
 */
public final class CreateBlocks {
    public static MachineBlock machine(MachineSize size, BlockBehaviour.Properties properties) {
        return new KineticMachineBlock(size, properties);
    }

    public static BlockEntity kineticMachine(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new KineticMachineBlockEntity(type, pos, state);
    }

    public static KineticTunnelWallBlock kineticTunnelWall(BlockBehaviour.Properties properties) {
        return new CreateKineticTunnelWallBlock(properties);
    }

    public static BlockEntity kineticTunnel(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        return new KineticTunnelBlockEntity(type, pos, state);
    }

    public static Item kineticTunnelItem(Item.Properties properties) {
        return new KineticTunnelItem(properties);
    }

    /** Game event listeners of the Create compat. */
    public static void register() {
        KineticLinks.register();
    }

    public static Class<? extends BlockEntity> kineticTunnelClass() {
        return KineticTunnelBlockEntity.class;
    }

    /** For Jade: a tunnel end's speed, the stress going through the tunnel, and whether it's overstressed. */
    public static void appendKineticData(BlockEntity entity, CompoundTag data) {
        if (!(entity instanceof LinkedKineticBlockEntity end)) return;
        data.putFloat("kinetic_rpm", end.getSpeed());
        data.putFloat("kinetic_su", end.passedStress());
        data.putBoolean("kinetic_overstressed", end.isOverStressed());
    }

    /** False for a machine block entity that can't carry a shaft (a plain one, placed before Create). */
    public static boolean isKineticMachine(BlockEntity entity) {
        return entity instanceof KineticMachineBlockEntity;
    }

    public static Class<? extends BlockEntity> kineticMachineClass() {
        return KineticMachineBlockEntity.class;
    }

    private CreateBlocks() {}
}
