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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
