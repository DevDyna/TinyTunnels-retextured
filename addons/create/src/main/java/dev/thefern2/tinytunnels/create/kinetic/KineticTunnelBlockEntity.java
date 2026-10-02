package dev.thefern2.tinytunnels.create.kinetic;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.create.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** The kinetic tunnel wall's end of the link (see {@link LinkedKineticBlockEntity}). It drives the room on an IN tunnel. */
public class KineticTunnelBlockEntity extends LinkedKineticBlockEntity {
    public KineticTunnelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KINETIC_TUNNEL.get(), pos, state);
    }

    /** Linked while the room has a kinetic tunnel whose wall is this block. */
    @Override
    protected @Nullable Link link() {
        if (!(level instanceof ServerLevel rooms)) return null;
        RoomView room = TunnelService.get().roomAt(rooms, worldPosition).orElse(null);
        Tunnel<KineticMode> tunnel = room == null ? null
                : room.tunnelAt(worldPosition).flatMap(found -> found.as(ModTunnelKinds.KINETIC.get())).orElse(null);
        if (tunnel == null) return null;
        return new Link(room.id(), worldPosition, tunnel.data() == KineticMode.IN, KineticLinks.speedFactor(tunnel.inward(), tunnel.face()));
    }

    @Override
    protected KineticMode drivingMode() {
        return KineticMode.IN;
    }
}
