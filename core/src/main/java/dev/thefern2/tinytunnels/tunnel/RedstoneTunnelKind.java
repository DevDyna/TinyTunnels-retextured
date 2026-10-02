package dev.thefern2.tinytunnels.tunnel;

import java.util.UUID;

import com.mojang.serialization.Codec;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.api.TunnelContext;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code tinytunnels:redstone}: a one-way redstone signal. The data is the direction and the last signal read
 * ({@link RedstoneSignal}). The machine side stays inside core ({@link RedstoneTunnels}).
 */
public final class RedstoneTunnelKind implements TunnelKind<RedstoneSignal>, CoreKind<RedstoneSignal> {
    private static final FaceLook DARK = new FaceLook(TinyTunnels.id("block/machine_port_redstone"));
    private static final FaceLook LIT = new FaceLook(TinyTunnels.id("block/machine_port_redstone_on"));

    @Override
    public Codec<RedstoneSignal> dataCodec() {
        return RedstoneSignal.CODEC;
    }

    /** A new tunnel carries the signal in, from outside the machine into the room, and starts dark. */
    @Override
    public RedstoneSignal defaultData() {
        return new RedstoneSignal(RedstoneMode.IN, 0);
    }

    @Override
    public Block wallBlock() {
        return ModBlocks.REDSTONE_TUNNEL_WALL.get();
    }

    @Override
    public BlockState wallState(Direction face, Direction inward, RedstoneSignal signal) {
        return wallBlock().defaultBlockState().setValue(RedstoneTunnelWallBlock.FACE, face).setValue(RedstoneTunnelWallBlock.INWARD, inward)
                .setValue(RedstoneTunnelWallBlock.MODE, signal.mode()).setValue(RedstoneTunnelWallBlock.POWERED, signal.power() > 0);
    }

    @Override
    public ItemStack item(RedstoneSignal signal) {
        return new ItemStack(ModItems.REDSTONE_TUNNEL.get());
    }

    /** Flips the direction; the signal starts again from 0. */
    @Override
    public InteractionResult useWithoutItem(TunnelContext<RedstoneSignal> context, Player player) {
        ServerLevel rooms = context.level();
        Direction face = context.tunnel().face();
        RedstoneMode mode = context.tunnel().data().mode().flip();
        TunnelChanges.setData(rooms, context.room().id(), face, this, context.tunnel().data().withMode(mode));
        rooms.playSound(null, context.tunnel().wall(), SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, mode == RedstoneMode.IN ? 0.5f : 0.55f);
        player.displayClientMessage(RedstoneTunnels.modeMessage(mode, face), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public FaceLook faceLook(RedstoneSignal signal) {
        return signal.power() > 0 ? LIT : DARK;
    }

    @Override
    public Component displayName() {
        return Component.translatableWithFallback("tunnel_kind.tinytunnels.redstone", "Redstone Tunnel");
    }

    @Override
    public Component describe(RedstoneSignal signal) {
        return Component.translatableWithFallback("tunnel_kind.tinytunnels.redstone." + signal.mode().getSerializedName(),
                signal.mode() == RedstoneMode.IN ? "In, signal %s" : "Out, signal %s", signal.power());
    }

    @Override
    public Component placedMessage(Direction face, RedstoneSignal signal) {
        return RedstoneTunnels.modeMessage(signal.mode(), face);
    }

    @Override
    public boolean hasCapabilities() {
        return false;
    }

    /** The new face starts from 0 and reads again. */
    @Override
    public RedstoneSignal movedData(RedstoneSignal signal) {
        return signal.withPower(0);
    }

    @Override
    public void added(MinecraftServer server, UUID roomId, Direction face) {
        RedstoneTunnels.tunnelChanged(server, roomId, face);
    }

    /** The old face stops emitting. */
    @Override
    public void moved(MinecraftServer server, UUID roomId, Direction oldFace, Direction newFace) {
        RedstoneTunnels.updateMachine(server, roomId, oldFace);
        RedstoneTunnels.tunnelChanged(server, roomId, newFace);
    }

    @Override
    public void removed(MinecraftServer server, UUID roomId, Direction face) {
        RedstoneTunnels.updateMachine(server, roomId, face);
    }

    /** A new direction: both ends update, and the reading end reads. Power alone goes through {@link TunnelChanges#recordData}. */
    @Override
    public void dataChanged(MinecraftServer server, UUID roomId, Direction face, RedstoneSignal oldData, RedstoneSignal newData) {
        RedstoneTunnels.tunnelChanged(server, roomId, face);
    }
}
