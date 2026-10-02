package dev.thefern2.tinytunnels.create.port;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.api.MachineView;
import dev.thefern2.tinytunnels.api.RoomView;
import dev.thefern2.tinytunnels.api.Tunnel;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.create.kinetic.KineticLinks;
import dev.thefern2.tinytunnels.create.kinetic.KineticMode;
import dev.thefern2.tinytunnels.create.kinetic.LinkedKineticBlockEntity;
import dev.thefern2.tinytunnels.create.registry.ModBlockEntities;
import dev.thefern2.tinytunnels.create.registry.ModTunnelKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The port's end of the link (see {@link LinkedKineticBlockEntity}): what the kinetic machine was before the addon.
 * Each tick it asks for the machine behind it, that machine's room, and a kinetic tunnel on the face it sits on.
 * With none it doesn't turn, and Jade and the goggles say it isn't linked. It drives the outside on an OUT tunnel.
 */
public class KineticPortBlockEntity extends LinkedKineticBlockEntity {
    public KineticPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KINETIC_PORT.get(), pos, state);
    }

    /** The machine face this port sits on (its shaft points out along it). */
    public Direction face() {
        return getBlockState().getValue(KineticPortBlock.FACING);
    }

    @Override
    protected @Nullable Link link() {
        if (!(level instanceof ServerLevel)) return null;
        Direction face = face();
        RoomView room = TunnelService.get().machineAt(level, worldPosition.relative(face.getOpposite()))
                .flatMap(MachineView::hostedRoom).orElse(null);
        Tunnel<KineticMode> tunnel = room == null ? null : room.tunnel(face, ModTunnelKinds.KINETIC.get()).orElse(null);
        if (tunnel == null) return null;
        return new Link(room.id(), tunnel.wall(), tunnel.data() == KineticMode.OUT, KineticLinks.speedFactor(tunnel.inward(), face));
    }

    @Override
    protected KineticMode drivingMode() {
        return KineticMode.OUT;
    }
}
