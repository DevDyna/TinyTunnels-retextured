package dev.thefern2.tinytunnels.machine;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Room sizes, one machine block per size. {@code interior} is the inside width in blocks;
 * with walls the room is {@code interior + 2} wide, which must fit in one chunk.
 */
public enum MachineSize implements StringRepresentable {
    TINY("tiny", 3),
    SMALL("small", 5),
    NORMAL("normal", 7),
    LARGE("large", 9),
    GIANT("giant", 11),
    MAXIMUM("maximum", 13);

    public static final Codec<MachineSize> CODEC = StringRepresentable.fromEnum(MachineSize::values);

    private final String name;
    private final int interior;

    MachineSize(String name, int interior) {
        this.name = name;
        this.interior = interior;
    }

    public String getName() {
        return name;
    }

    public int getInterior() {
        return interior;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public String blockId() {
        return name + "_machine";
    }
}
