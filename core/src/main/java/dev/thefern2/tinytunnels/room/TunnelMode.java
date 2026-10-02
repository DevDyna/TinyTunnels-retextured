package dev.thefern2.tinytunnels.room;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * How an item/fluid/energy tunnel carries things. Pass-through hands every request straight to the
 * block on the other side. The buffered modes hold a little themselves (items and fluids only), so a
 * pipe can push in on one side and another pipe can pull out on the other; they're one-way.
 */
public enum TunnelMode implements StringRepresentable {
    PASSTHROUGH("passthrough"),
    /** Outside the machine into the room: the machine face fills the buffer, the tunnel wall drains it. */
    BUFFERED_IN("buffered_in"),
    /** The room to outside the machine: the tunnel wall fills the buffer, the machine face drains it. */
    BUFFERED_OUT("buffered_out");

    public static final Codec<TunnelMode> CODEC = StringRepresentable.fromEnum(TunnelMode::values);

    private final String name;

    TunnelMode(String name) {
        this.name = name;
    }

    public boolean isBuffered() {
        return this != PASSTHROUGH;
    }

    public TunnelMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component displayName() {
        return Component.translatable("tinytunnels.tunnel_mode." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
