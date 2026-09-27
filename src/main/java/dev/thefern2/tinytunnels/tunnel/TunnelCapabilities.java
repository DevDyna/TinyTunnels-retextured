package dev.thefern2.tinytunnels.tunnel;

import org.jspecify.annotations.Nullable;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.machine.MachineBlock;
import dev.thefern2.tinytunnels.machine.MachineHost;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Registers the two halves of every tunnel, for items, fluids and energy:
 * <ul>
 *   <li>a pipe outside touching machine face F gets the block next to F's tunnel, inside the room;</li>
 *   <li>a pipe inside touching a tunnel gets the block next to the machine's face F, outside.</li>
 * </ul>
 * Nothing is buffered: the handler returned belongs to the block on the other side, so the pipe's
 * own transaction reaches it directly. The lookup never throws and never loads chunks.
 */
@EventBusSubscriber(modid = TinyTunnels.MODID)
public final class TunnelCapabilities {
    private static boolean warned;

    @SubscribeEvent
    static void register(RegisterCapabilitiesEvent event) {
        Block[] machines = ModBlocks.MACHINES.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new);
        for (TransferKind<?> kind : TransferKind.ALL) {
            registerFor(event, kind, machines);
        }
    }

    private static <T> void registerFor(RegisterCapabilitiesEvent event, TransferKind<T> kind, Block[] machines) {
        event.registerBlock(kind.capability(), (level, pos, state, be, side) -> fromMachine(kind, level, state, be, side), machines);
        event.registerBlock(kind.capability(), (level, pos, state, be, side) -> fromTunnel(kind, level, state, be, side), ModBlocks.TUNNEL_WALL.get());
    }

    // On the client there's no room data. Answer "empty but present" on exactly the sides that are
    // tunnels, as the block state says, so client-side checks (e.g. Pipez keeping its extract flag)
    // agree with the server. Empty handlers never move anything.

    private static <T> @Nullable T fromMachine(TransferKind<T> kind, Level level, BlockState state, @Nullable BlockEntity be, @Nullable Direction side) {
        if (side == null) return null;
        if (level.isClientSide()) return MachineBlock.hasTunnel(state, side) ? kind.empty() : null;
        if (!(be instanceof MachineHost machine)) return null;
        try {
            return ProxyGuard.lookup(kind, () -> machine.insideCapability(kind, side));
        } catch (RuntimeException e) {
            warnOnce(e);
            return kind.empty();
        }
    }

    private static <T> @Nullable T fromTunnel(TransferKind<T> kind, Level level, BlockState state, @Nullable BlockEntity be, @Nullable Direction side) {
        if (side == null) return null;
        if (level.isClientSide()) return side == state.getValue(TunnelWallBlock.INWARD) ? kind.empty() : null;
        if (!(be instanceof TunnelBlockEntity tunnel)) return null;
        try {
            if (side != tunnel.inward()) return null;
            return ProxyGuard.lookup(kind, () -> tunnel.outsideCapability(kind));
        } catch (RuntimeException e) {
            warnOnce(e);
            return kind.empty();
        }
    }

    private static void warnOnce(RuntimeException e) {
        if (warned) return;
        warned = true;
        TinyTunnels.LOGGER.error("Tunnel capability lookup failed; answering empty. Logged once.", e);
    }

    private TunnelCapabilities() {}
}
