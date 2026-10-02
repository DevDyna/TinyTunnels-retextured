package dev.thefern2.tinytunnels.compat.create;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.machine.MachineCore;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.registry.ModDataComponents;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The machine block entity when Create is loaded: one end of a kinetic tunnel on its kinetic face (see
 * {@link LinkedKineticBlockEntity}), and a normal machine otherwise. The machine logic is in
 * {@link MachineCore}, as in {@code MachineBlockEntity}; the hooks differ because Create makes
 * {@code setRemoved} and {@code saveAdditional} final. It drives the outside on an OUT tunnel.
 */
public class KineticMachineBlockEntity extends LinkedKineticBlockEntity implements MachineHost {
    private final MachineCore core = new MachineCore(this);

    public KineticMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Always {@code kinetic_machine}, the type {@link KineticMachineBlock#newBlockEntity} makes, even when loaded
     * under {@code machine} (saved before Create): the client drops update tags whose type doesn't match the
     * block entity it built from the block (see {@code MachineBlockEntity#getType}).
     */
    @Override
    public BlockEntityType<?> getType() {
        return ModBlockEntities.KINETIC_MACHINE.get();
    }

    @Override
    public MachineCore core() {
        return core;
    }

    public @Nullable Direction kineticFace() {
        return KineticMachineBlock.kineticFace(getBlockState());
    }

    @Override
    protected @Nullable Link link() {
        Direction face = kineticFace();
        Room room = face == null ? null : hostedRoom().orElse(null);
        RoomTunnel<RedstoneMode> tunnel = room == null ? null : room.tunnel(face, ModTunnelKinds.KINETIC.get());
        Direction inward = tunnel == null ? null : room.geometry().inwardNormal(tunnel.wall());
        if (inward == null) return null;
        return new Link(room.id(), tunnel.wall(), tunnel.data() == RedstoneMode.OUT, KineticLinks.speedFactor(inward, face));
    }

    // Machine hooks, forwarded to the core.

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

    /** Called from Create's final {@code setRemoved}, on removal and on unload alike. */
    @Override
    public void invalidate() {
        super.invalidate();
        core.setRemoved();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        // Create's update tag and packets come through here with clientPacket set: they carry the face looks.
        if (clientPacket) {
            core.writeClient(tag);
        } else {
            core.save(tag);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            core.readClient(tag);
        } else {
            core.load(tag);
        }
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
