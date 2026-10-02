package dev.thefern2.tinytunnels.create.kinetic;

import com.mojang.serialization.Codec;

import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.api.TunnelContext;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelService;
import dev.thefern2.tinytunnels.create.TinyTunnelsCreate;
import dev.thefern2.tinytunnels.create.registry.ModBlocks;
import dev.thefern2.tinytunnels.create.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code tinytunnels_create:kinetic}: Create rotation and stress through a machine face. The wall end is
 * {@link KineticTunnelWallBlock} inside the room; the outside end is the kinetic port the player places against the
 * machine face. The data is the direction, {@link KineticMode}.
 */
public final class KineticTunnelKind implements TunnelKind<KineticMode> {
    private static final FaceLook LOOK = new FaceLook(TinyTunnelsCreate.id("block/machine_port_kinetic"));

    @Override
    public Codec<KineticMode> dataCodec() {
        return KineticMode.CODEC;
    }

    /** A new tunnel carries rotation out of the room, the main use. */
    @Override
    public KineticMode defaultData() {
        return KineticMode.OUT;
    }

    @Override
    public Block wallBlock() {
        return ModBlocks.KINETIC_TUNNEL_WALL.get();
    }

    @Override
    public BlockState wallState(Direction face, Direction inward, KineticMode mode) {
        return wallBlock().defaultBlockState()
                .setValue(KineticTunnelWallBlock.FACE, face)
                .setValue(KineticTunnelWallBlock.INWARD, inward)
                .setValue(KineticTunnelWallBlock.MODE, mode);
    }

    /** One per face. Loops between tunnels are stopped by the loop guard ({@link KineticLinks#decide}). */
    @Override
    public int maxPerRoom() {
        return 6;
    }

    @Override
    public ItemStack item(KineticMode mode) {
        return new ItemStack(ModItems.KINETIC_TUNNEL.get());
    }

    /** Flips IN and OUT. Both ends notice on their next tick. */
    @Override
    public InteractionResult useWithoutItem(TunnelContext<KineticMode> context, Player player) {
        Direction face = context.tunnel().face();
        KineticMode mode = context.tunnel().data().flip();
        if (!TunnelService.get().setData(context.room(), face, this, mode)) return InteractionResult.PASS;
        context.level().playSound(null, context.tunnel().wall(), SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.3f, mode == KineticMode.IN ? 0.5f : 0.55f);
        player.displayClientMessage(modeMessage(mode, face), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public FaceLook faceLook(KineticMode mode) {
        return LOOK;
    }

    @Override
    public Component displayName() {
        return Component.translatable("tunnel_kind.tinytunnels_create.kinetic");
    }

    @Override
    public Component describe(KineticMode mode) {
        return Component.translatable("tunnel_kind.tinytunnels_create.kinetic." + mode.getSerializedName());
    }

    /** Shown by core after a placement or a wrench move. */
    @Override
    public Component placedMessage(Direction face, KineticMode mode) {
        return modeMessage(mode, face);
    }

    /** "Rotation out to the machine's east side". */
    public static Component modeMessage(KineticMode mode, Direction face) {
        return Component.translatable("message.tinytunnels_create.kinetic_tunnel." + mode.getSerializedName(), faceName(face));
    }

    /** "east", "top", ... */
    public static Component faceName(Direction face) {
        return Component.translatable("tinytunnels_create.face." + face.getSerializedName());
    }
}
