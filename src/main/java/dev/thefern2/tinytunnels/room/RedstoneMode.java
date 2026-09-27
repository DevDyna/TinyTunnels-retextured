package dev.thefern2.tinytunnels.room;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Which way a redstone tunnel carries its signal. One-way on purpose: an end that both read and
 * emitted would read back its own output and never turn off.
 */
public enum RedstoneMode implements StringRepresentable {
    /** From outside the machine into the room: the machine reads, the tunnel wall emits. */
    IN("in"),
    /** From the room to outside the machine: the tunnel wall reads, the machine emits. */
    OUT("out");

    public static final Codec<RedstoneMode> CODEC = StringRepresentable.fromEnum(RedstoneMode::values);

    private final String name;

    RedstoneMode(String name) {
        this.name = name;
    }

    public RedstoneMode flip() {
        return this == IN ? OUT : IN;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
