package dev.thefern2.tinytunnels.create.kinetic;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/** Which way a kinetic tunnel carries rotation. The per-tunnel data of {@link KineticTunnelKind}. */
public enum KineticMode implements StringRepresentable {
    /** From outside the machine into the room: the port is the consumer, the tunnel wall drives the room. */
    IN("in"),
    /** From the room to outside the machine: the tunnel wall is the consumer, the port drives the outside. */
    OUT("out");

    public static final Codec<KineticMode> CODEC = StringRepresentable.fromEnum(KineticMode::values);

    private final String name;

    KineticMode(String name) {
        this.name = name;
    }

    public KineticMode flip() {
        return this == IN ? OUT : IN;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
