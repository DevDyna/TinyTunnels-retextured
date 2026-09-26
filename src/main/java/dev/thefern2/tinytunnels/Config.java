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

    static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
