package dev.thefern2.tinytunnels.machine;

import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The plain machine block entity. All machine logic is in {@link MachineCore}; this only forwards the hooks.
 * Removal side effects are forwarded from {@link MachineBlock#onRemove}, which 1.21.1 calls before the block entity goes.
 */
public class MachineBlockEntity extends BlockEntity implements MachineHost {
    private final MachineCore core = new MachineCore(this);

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.MACHINE.get(), pos, state);
    }

    /** For {@link ModBlockEntities#KINETIC_MACHINE}'s fallback when Create isn't loaded. */
    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Always {@code machine}, the type {@link MachineBlock#newBlockEntity} makes, even when this was loaded from a
     * save under {@code kinetic_machine} (a world saved with Create, opened without it). The client builds its
     * block entity from the block, and drops the chunk's and every later update tag whose type doesn't match
     * ({@code LevelChunk.replaceWithPacketData}, {@code ClientPacketListener.handleBlockEntityData}), so a
     * mismatch loses the face looks. It's also saved under this id from then on.
     */
    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.MACHINE.get();
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

    // Face looks (see MachineCore#syncFaces).

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        core.writeClient(tag);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        core.readClient(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        core.readClient(packet.getTag());
    }

    @Override
    public ModelData getModelData() {
        return core.modelData();
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
