package dev.thefern2.tinytunnels.compat.jade;

import dev.thefern2.tinytunnels.compat.Compat;
import dev.thefern2.tinytunnels.compat.create.CreateBlocks;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModTunnelKinds;
import dev.thefern2.tinytunnels.room.RoomTunnel;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.tunnel.RedstoneSignal;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import dev.thefern2.tinytunnels.tunnel.TunnelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * What the client can't know from block states, from {@link RoomData} on the server: a machine's tunnels
 * with their kind names and status, a buffered tunnel's contents, and a redstone tunnel wall's {@code power}.
 */
enum ServerData implements IServerDataProvider<BlockAccessor> {
    /**
     * The hosted room's tunnels, as a list {@code faces} of {@code {face, kind, name, status}}: the kind's
     * {@code displayName} and {@code describe(data)} (no status when it has none), as text components.
     */
    MACHINE(TinyTunnelsJadePlugin.MACHINE) {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MachineHost machine)) return;
            RegistryOps<Tag> ops = accessor.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
            machine.hostedRoom().ifPresent(room -> {
                ListTag faces = new ListTag();
                room.faces().forEach((face, tunnel) -> faces.add(face(face, tunnel, ops)));
                data.put("faces", faces);
            });
            if (Compat.CREATE) CreateBlocks.appendKineticData(accessor.getBlockEntity(), data);
        }
    },
    /** A buffered tunnel's contents, under {@code items} (list of stacks) and {@code fluid}. */
    TUNNEL(TinyTunnelsJadePlugin.TUNNEL) {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof TunnelBlockEntity tunnel) || !tunnel.mode().isBuffered()) return;
            HolderLookup.Provider registries = accessor.getLevel().registryAccess();
            ListTag items = new ListTag();
            tunnel.buffer().contents().forEach(stack -> items.add(stack.save(registries)));
            data.put("items", items);
            FluidStack fluid = tunnel.buffer().fluidContents();
            if (!fluid.isEmpty()) data.put("fluid", fluid.save(registries));
        }
    },
    /** The kinetic tunnel wall's speed and the stress it passes on ({@code kinetic_rpm}, {@code kinetic_su}, {@code kinetic_overstressed}). */
    KINETIC_TUNNEL(TinyTunnelsJadePlugin.KINETIC_TUNNEL) {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (Compat.CREATE) CreateBlocks.appendKineticData(accessor.getBlockEntity(), data);
        }
    },
    REDSTONE_TUNNEL(TinyTunnelsJadePlugin.REDSTONE_TUNNEL) {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel rooms)) return;
            BlockPos pos = accessor.getPosition();
            Room room = RoomData.get(rooms.getServer()).byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).orElse(null);
            Direction face = accessor.getBlockState().getValue(RedstoneTunnelWallBlock.FACE);
            RoomTunnel<RedstoneSignal> tunnel = room == null ? null : room.tunnel(face, ModTunnelKinds.REDSTONE.get());
            if (tunnel != null && tunnel.wall().equals(pos)) data.putInt("power", tunnel.data().power());
        }
    };

    private static <D> CompoundTag face(Direction face, RoomTunnel<D> tunnel, RegistryOps<Tag> ops) {
        CompoundTag entry = new CompoundTag();
        entry.putString("face", face.getSerializedName());
        ResourceLocation kind = TunnelKinds.REGISTRY.getKey(tunnel.kind());
        if (kind != null) entry.putString("kind", kind.toString());
        entry.put("name", ComponentSerialization.CODEC.encodeStart(ops, tunnel.kind().displayName()).getOrThrow());
        Component status = tunnel.kind().describe(tunnel.data());
        if (status != null) entry.put("status", ComponentSerialization.CODEC.encodeStart(ops, status).getOrThrow());
        return entry;
    }

    private final ResourceLocation uid;

    ServerData(ResourceLocation uid) {
        this.uid = uid;
    }

    @Override
    public ResourceLocation getUid() {
        return uid;
    }
}
