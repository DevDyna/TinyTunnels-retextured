package dev.thefern2.tinytunnels.compat.jade;

import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.room.RedstoneTunnel;
import dev.thefern2.tinytunnels.room.Room;
import dev.thefern2.tinytunnels.room.RoomData;
import dev.thefern2.tinytunnels.tunnel.RedstoneTunnelWallBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * What the client can't know from block states: each redstone tunnel's direction and signal, which
 * live in {@link RoomData} on the server. Keys are {@code redstone_<face>} (mode) and
 * {@code power_<face>}; for a redstone tunnel wall, just {@code power}.
 */
enum ServerData implements IServerDataProvider<BlockAccessor> {
    MACHINE(TinyTunnelsJadePlugin.MACHINE) {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof MachineHost machine)) return;
            machine.hostedRoom().ifPresent(room -> room.redstone().forEach((face, tunnel) -> {
                data.putString("redstone_" + face.getSerializedName(), tunnel.mode().getSerializedName());
                data.putInt("power_" + face.getSerializedName(), tunnel.power());
            }));
        }
    },
    REDSTONE_TUNNEL(TinyTunnelsJadePlugin.REDSTONE_TUNNEL) {
        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getLevel() instanceof ServerLevel rooms)) return;
            BlockPos pos = accessor.getPosition();
            Room room = RoomData.get(rooms.getServer()).byChunk(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).orElse(null);
            RedstoneTunnel tunnel = room == null ? null : room.redstone().get(accessor.getBlockState().getValue(RedstoneTunnelWallBlock.FACE));
            if (tunnel != null && tunnel.pos().equals(pos)) data.putInt("power", tunnel.power());
        }
    };

    private final Identifier uid;

    ServerData(Identifier uid) {
        this.uid = uid;
    }

    @Override
    public Identifier getUid() {
        return uid;
    }
}
