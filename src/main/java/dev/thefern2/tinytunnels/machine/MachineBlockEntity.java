package dev.thefern2.tinytunnels.machine;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** The plain machine block entity. All machine logic is in {@link MachineCore}; this only forwards the hooks. */
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
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        core.preRemoveSideEffects(pos);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        core.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        core.save(output);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        core.applyImplicitComponents(components);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        core.collectImplicitComponents(components);
    }
}
