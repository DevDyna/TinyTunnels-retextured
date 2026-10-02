package dev.thefern2.tinytunnels.tunnel;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.compat.Compat;
import dev.thefern2.tinytunnels.compat.create.CreateBlocks;
import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.api.PlacementContext;
import dev.thefern2.tinytunnels.api.TunnelContext;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.registry.ModBlocks;
import dev.thefern2.tinytunnels.registry.ModItems;
import dev.thefern2.tinytunnels.room.RedstoneMode;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code tinytunnels:kinetic}, temporary: the kinetic tunnel (Create rotation) as a kind inside core, so rooms keep
 * it in the one tunnel map. Create-free: the wall is {@code ModBlocks.KINETIC_TUNNEL_WALL}, which is the Create wall
 * with Create and the inert one without. A6 replaces this with the Create addon's {@code tinytunnels_create:kinetic}.
 */
public final class KineticTunnelKind implements TunnelKind<RedstoneMode>, CoreKind<RedstoneMode> {
    private static final FaceLook LOOK = new FaceLook(TinyTunnels.id("block/machine_port_kinetic"));

    @Override
    public Codec<RedstoneMode> dataCodec() {
        return RedstoneMode.CODEC;
    }

    /** A new tunnel carries rotation out of the room. */
    @Override
    public RedstoneMode defaultData() {
        return RedstoneMode.OUT;
    }

    @Override
    public Block wallBlock() {
        return ModBlocks.KINETIC_TUNNEL_WALL.get();
    }

    @Override
    public BlockState wallState(Direction face, Direction inward, RedstoneMode mode) {
        return wallBlock().defaultBlockState().setValue(KineticTunnelWallBlock.FACE, face).setValue(KineticTunnelWallBlock.INWARD, inward)
                .setValue(KineticTunnelWallBlock.MODE, mode);
    }

    /** The machine is a single Create block, so a second kinetic face would join two networks. */
    @Override
    public int maxPerRoom() {
        return 1;
    }

    /** The item exists only with Create; an inert wall left from a Create world gives nothing back. */
    @Override
    public ItemStack item(RedstoneMode mode) {
        return ModItems.KINETIC_TUNNEL == null ? ItemStack.EMPTY : new ItemStack(ModItems.KINETIC_TUNNEL.get());
    }

    /** Flips IN and OUT. Both ends notice on their next tick. */
    @Override
    public InteractionResult useWithoutItem(TunnelContext<RedstoneMode> context, Player player) {
        ServerLevel rooms = context.level();
        Direction face = context.tunnel().face();
        RedstoneMode mode = context.tunnel().data().flip();
        TunnelChanges.setData(rooms, context.room().id(), face, this, mode);
        rooms.playSound(null, context.tunnel().wall(), SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, mode == RedstoneMode.IN ? 0.5f : 0.55f);
        player.displayClientMessage(KineticTunnels.modeMessage(mode, face), true);
        return InteractionResult.SUCCESS;
    }

    /** With Create, a machine with a plain block entity (placed before Create) can't carry a shaft. */
    @Override
    public @Nullable Component refusePlacement(PlacementContext<RedstoneMode> context) {
        if (!Compat.CREATE) return null;
        GlobalPos host = context.room().host().orElse(null);
        ServerLevel level = host == null ? null : context.level().getServer().getLevel(host.dimension());
        if (level == null || !level.isLoaded(host.pos())) return null;
        BlockEntity machine = level.getBlockEntity(host.pos());
        return machine != null && CreateBlocks.isKineticMachine(machine) ? null
                : Component.translatable("message.tinytunnels.kinetic_tunnel.plain_machine");
    }

    @Override
    public boolean hasCapabilities() {
        return false;
    }

    @Override
    public FaceLook faceLook(RedstoneMode mode) {
        return LOOK;
    }

    @Override
    public Component displayName() {
        return Component.translatableWithFallback("tunnel_kind.tinytunnels.kinetic", "Kinetic Tunnel");
    }

    @Override
    public Component describe(RedstoneMode mode) {
        return Component.translatableWithFallback("tunnel_kind.tinytunnels.kinetic." + mode.getSerializedName(), mode == RedstoneMode.IN ? "In" : "Out");
    }

    @Override
    public Component placedMessage(Direction face, RedstoneMode mode) {
        return KineticTunnels.modeMessage(mode, face);
    }
}
