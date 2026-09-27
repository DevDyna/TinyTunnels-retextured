package dev.thefern2.tinytunnels.room;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;

/**
 * A redstone tunnel: where its wall is, which way it carries the signal, and the last signal the
 * reading end saw. The emitting end emits {@code power}, so it's right even straight after loading.
 */
public record RedstoneTunnel(BlockPos pos, RedstoneMode mode, int power) {
    public static final Codec<RedstoneTunnel> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(RedstoneTunnel::pos),
            RedstoneMode.CODEC.fieldOf("mode").forGetter(RedstoneTunnel::mode),
            ExtraCodecs.intRange(0, 15).optionalFieldOf("power", 0).forGetter(RedstoneTunnel::power)
    ).apply(i, RedstoneTunnel::new));

    public RedstoneTunnel {
        pos = pos.immutable();
    }

    public RedstoneTunnel withPos(BlockPos pos) {
        return new RedstoneTunnel(pos, mode, power);
    }

    /** A new direction starts dark: the old reading no longer applies. */
    public RedstoneTunnel withMode(RedstoneMode mode) {
        return new RedstoneTunnel(pos, mode, 0);
    }

    public RedstoneTunnel withPower(int power) {
        return new RedstoneTunnel(pos, mode, power);
    }
}
