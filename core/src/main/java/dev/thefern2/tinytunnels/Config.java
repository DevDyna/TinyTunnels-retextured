package dev.thefern2.tinytunnels;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue SHRINK_ANIMATION = BUILDER
            .comment("Shrink the player into the machine before teleporting into a room.")
            .define("shrinkAnimation", true);

    public static final ModConfigSpec.IntValue SHRINK_TICKS = BUILDER
            .comment("How long the shrink animation lasts, in ticks.")
            .defineInRange("shrinkTicks", 10, 1, 40);

    public static final ModConfigSpec.BooleanValue ROOM_BEDS_EXPLODE = BUILDER
            .comment("Let beds used inside a room explode, like in the Nether (vanilla 1.21.1 behaviour for a dimension without beds).",
                    "When false, beds inside rooms simply don't work.")
            .define("roomBedsExplode", false);

    public static final ModConfigSpec.IntValue BUFFER_ITEM_SLOTS = BUILDER
            .comment("Item stacks a buffered tunnel holds. Only affects tunnels created after a change; existing contents are kept.")
            .defineInRange("bufferItemSlots", 1, 1, 9);

    public static final ModConfigSpec.IntValue BUFFER_FLUID_CAPACITY = BUILDER
            .comment("Fluid (mB) a buffered tunnel holds.")
            .defineInRange("bufferFluidCapacity", 8000, 1000, 64000);

    static final ModConfigSpec SPEC = BUILDER.build();

    /** Server config values aren't loaded on a client or before the world starts; fall back to the defaults there. */
    public static int bufferItemSlots() {
        return SPEC.isLoaded() ? BUFFER_ITEM_SLOTS.getAsInt() : BUFFER_ITEM_SLOTS.getDefault();
    }

    public static int bufferFluidCapacity() {
        return SPEC.isLoaded() ? BUFFER_FLUID_CAPACITY.getAsInt() : BUFFER_FLUID_CAPACITY.getDefault();
    }

    private Config() {}
}
