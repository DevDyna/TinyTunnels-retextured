package dev.thefern2.tinytunnels.gametest;

import com.mojang.serialization.Codec;

import dev.thefern2.tinytunnels.TinyTunnels;
import dev.thefern2.tinytunnels.api.FaceLook;
import dev.thefern2.tinytunnels.api.TunnelKind;
import dev.thefern2.tinytunnels.api.TunnelKinds;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * {@code tinytunnels:test_kind}, a tunnel kind registered only when GameTests are on, the way an addon registers
 * one (A5.4). Its data is a number, and its wall is reinforced deepslate (a block no other test uses: a kind's wall counts as a shell block), so the tests can tell its wall from the others.
 */
final class TestTunnelKind implements TunnelKind<Integer> {
    private static final DeferredRegister<TunnelKind<?>> KINDS = DeferredRegister.create(TunnelKinds.REGISTRY_KEY, TinyTunnels.MODID);

    static final DeferredHolder<TunnelKind<?>, TestTunnelKind> KIND = KINDS.register("test_kind", TestTunnelKind::new);

    /** Called from {@link TinyTunnelsGameTests#register}, only when GameTests are on. */
    static void register(IEventBus modEventBus) {
        KINDS.register(modEventBus);
    }

    @Override
    public Codec<Integer> dataCodec() {
        return Codec.INT;
    }

    @Override
    public Integer defaultData() {
        return 0;
    }

    @Override
    public Block wallBlock() {
        return Blocks.REINFORCED_DEEPSLATE;
    }

    @Override
    public BlockState wallState(Direction face, Direction inward, Integer data) {
        return Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
    }

    @Override
    public ItemStack item(Integer data) {
        return ItemStack.EMPTY;
    }

    @Override
    public FaceLook faceLook(Integer data) {
        return new FaceLook(TinyTunnels.id("block/machine_port_transfer"));
    }

    @Override
    public Component displayName() {
        return Component.literal("Test Tunnel");
    }
}
