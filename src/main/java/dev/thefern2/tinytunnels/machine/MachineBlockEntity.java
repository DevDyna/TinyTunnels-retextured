package dev.thefern2.tinytunnels.machine;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The plain machine block entity. All machine logic is in {@link MachineCore}; this only forwards the hooks.
 * Removal side effects are forwarded from {@link MachineBlock#onRemove}, which 1.21.1 calls before the block entity goes.
 */
public class MachineBlockEntity extends BlockEntity implements MachineHost {
    private final MachineCore core = new MachineCore(this);

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MACHINE.get(), pos, state);
    }

    @Override
    public MachineCore core() {
        return core;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        core.onLoad();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        core.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        core.setRemoved();
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        core.load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        core.save(tag);
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput components) {
        super.applyImplicitComponents(components);
        core.applyImplicitRoom(components.get(ModDataComponents.ROOM_ID.get()));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        core.collectImplicitComponents(components);
    }
}
