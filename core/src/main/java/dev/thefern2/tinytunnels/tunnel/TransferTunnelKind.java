package dev.thefern2.tinytunnels.tunnel;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.api.TunnelContext;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.TunnelMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * {@code tinytunnels:transfer}: items, fluids and energy. The data is the {@link TunnelMode}; a buffered tunnel's
 * contents live on its {@link TunnelBlockEntity}. Capabilities are registered on the machine blocks and the wall
 * by {@link TunnelCapabilities}.
 */
public final class TransferTunnelKind implements TunnelKind<TunnelMode>, CoreKind<TunnelMode> {
    /** How long a "discard the fluid?" warning stays armed, in ticks. */
    private static final int CONFIRM_TICKS = 100;
    private static final FaceLook LOOK = new FaceLook(TinyTunnels.id("block/machine_port_transfer"));

    private record PendingDiscard(BlockPos pos, long expires) {}

    /** Players who were just warned that removing a tunnel throws its fluid away. */
    private static final Map<UUID, PendingDiscard> PENDING_DISCARD = new HashMap<>();

    @Override
    public Codec<TunnelMode> dataCodec() {
        return TunnelMode.CODEC;
    }

    @Override
    public TunnelMode defaultData() {
        return TunnelMode.PASSTHROUGH;
    }

    @Override
    public Block wallBlock() {
        return ModBlocks.TUNNEL_WALL.get();
    }

    @Override
    public BlockState wallState(Direction face, Direction inward, TunnelMode mode) {
        return wallBlock().defaultBlockState().setValue(TunnelWallBlock.FACE, face).setValue(TunnelWallBlock.INWARD, inward)
                .setValue(TunnelWallBlock.MODE, mode);
    }

    @Override
    public ItemStack item(TunnelMode mode) {
        return new ItemStack(ModItems.TUNNEL.get());
    }

    /** Fluid can't be handed back: the first click warns, a second one on the same tunnel soon after discards it. */
    @Override
    public @Nullable Component confirmRemoval(TunnelContext<TunnelMode> context, Player player) {
        BlockPos wall = context.tunnel().wall();
        if (!(context.level().getBlockEntity(wall) instanceof TunnelBlockEntity tunnel)) return null;
        FluidStack fluid = tunnel.fluidContents();
        if (fluid.isEmpty()) return null;
        PendingDiscard pending = PENDING_DISCARD.get(player.getUUID());
        long now = context.level().getGameTime();
        if (pending != null && pending.pos().equals(wall) && now <= pending.expires()) {
            PENDING_DISCARD.remove(player.getUUID());
            return null;
        }
        PENDING_DISCARD.put(player.getUUID(), new PendingDiscard(wall.immutable(), now + CONFIRM_TICKS));
        return Component.translatable("message.tinytunnels.tunnel.holds_fluid", fluid.getAmount(), fluid.getHoverName());
    }

    /** Items are handed back, like breaking a chest; fluid is discarded (the wrench asked first). */
    @Override
    public void onRemoving(TunnelContext<TunnelMode> context, @Nullable Player player) {
        BlockPos wall = context.tunnel().wall();
        if (!(context.level().getBlockEntity(wall) instanceof TunnelBlockEntity tunnel)) return;
        tunnel.discardFluid();
        BlockPos inside = wall.relative(context.tunnel().inward());
        for (ItemStack stack : tunnel.takeItems()) {
            if (player == null) {
                Containers.dropItemStack(context.level(), inside.getX() + 0.5, inside.getY() + 0.5, inside.getZ() + 0.5, stack);
            } else if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** Pass-through, buffered in, buffered out. Back to pass-through only once the buffer is empty. */
    @Override
    public InteractionResult useWithoutItem(TunnelContext<TunnelMode> context, Player player) {
        BlockPos wall = context.tunnel().wall();
        TunnelMode next = context.tunnel().data().next();
        if (!next.isBuffered() && context.level().getBlockEntity(wall) instanceof TunnelBlockEntity tunnel && !tunnel.buffer().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.not_empty"), true);
            return InteractionResult.SUCCESS;
        }
        TunnelChanges.setData(context.level(), context.room().id(), context.tunnel().face(), this, next);
        context.level().playSound(null, wall, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, next.isBuffered() ? 0.6f : 0.5f);
        player.displayClientMessage(Component.translatable("message.tinytunnels.tunnel.mode", next.displayName()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public FaceLook faceLook(TunnelMode mode) {
        return LOOK;
    }

    @Override
    public Component displayName() {
        return Component.translatableWithFallback("tunnel_kind.tinytunnels.transfer", "Tunnel");
    }

    @Override
    public Component describe(TunnelMode mode) {
        return mode.displayName();
    }

    @Override
    public Component placedMessage(Direction face, TunnelMode mode) {
        return Component.translatable("message.tinytunnels.tunnel.mapped", TunnelWallBlock.faceName(face));
    }

    static void clearPending() {
        PENDING_DISCARD.clear();
    }
}
