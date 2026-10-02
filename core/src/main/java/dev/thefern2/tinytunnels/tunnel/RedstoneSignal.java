package dev.thefern2.tinytunnels.tunnel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.thefern2.tinytunnels.room.RedstoneMode;
import net.minecraft.util.ExtraCodecs;

/**
 * A redstone tunnel's data: which way it carries the signal, and the last signal the reading end saw. The
 * emitting end emits {@code power}, so it's right even straight after loading.
 */
public record RedstoneSignal(RedstoneMode mode, int power) {
    public static final Codec<RedstoneSignal> CODEC = RecordCodecBuilder.create(i -> i.group(
            RedstoneMode.CODEC.fieldOf("mode").forGetter(RedstoneSignal::mode),
            ExtraCodecs.intRange(0, 15).optionalFieldOf("power", 0).forGetter(RedstoneSignal::power)
    ).apply(i, RedstoneSignal::new));

    /** A new direction starts dark: the old reading no longer applies. */
    public RedstoneSignal withMode(RedstoneMode mode) {
        return new RedstoneSignal(mode, 0);
    }

    public RedstoneSignal withPower(int power) {
        return new RedstoneSignal(mode, power);
    }
}
