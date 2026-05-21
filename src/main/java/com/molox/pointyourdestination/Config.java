package com.molox.pointyourdestination;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue SOUND_VOLUME;
    public static final ModConfigSpec.BooleanValue ANIMATION_ENABLED;

    static {
        BUILDER.comment("Sound Settings");
        SOUND_VOLUME = BUILDER
                .comment("Volume of the waypoint placement sound. Range: 0.0 to 1.0")
                .defineInRange("soundVolume", 0.5, 0.0, 1.0);

        BUILDER.comment("Animation Settings");
        ANIMATION_ENABLED = BUILDER
                .comment("Whether to play the crosshair animation when placing a waypoint")
                .define("animationEnabled", true);

        SPEC = BUILDER.build();
    }

    public static final ModConfigSpec SPEC;
}