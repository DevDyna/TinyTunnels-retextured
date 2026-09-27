package dev.thefern2.tinytunnels.machine;

import net.minecraft.util.StringRepresentable;

/** What a machine face is linked to inside the room. Picks the overlay on that face. */
public enum PortKind implements StringRepresentable {
    NONE("none"),
    /** An item, fluid and energy tunnel. */
    TUNNEL("tunnel"),
    /** A redstone tunnel carrying no signal. */
    REDSTONE("redstone"),
    /** A redstone tunnel carrying a signal, either way. Only changes the look. */
    REDSTONE_ON("redstone_on");

    public boolean isRedstone() {
        return this == REDSTONE || this == REDSTONE_ON;
    }

    private final String name;

    PortKind(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
