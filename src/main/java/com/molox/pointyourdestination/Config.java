package com.molox.pointyourdestination;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue SOUND_VOLUME;
    public static final ModConfigSpec.BooleanValue ANIMATION_ENABLED;
    public static final ModConfigSpec.BooleanValue SCROLL_ZOOM_ENABLED;
    public static final ModConfigSpec.DoubleValue SCROLL_ZOOM_MIN;
    public static final ModConfigSpec.DoubleValue SCROLL_ZOOM_MAX;

    static {
        BUILDER.comment("Sound Settings");
        SOUND_VOLUME = BUILDER
                .comment("Volume of the waypoint placement sound. Range: 0.0 to 1.0")
                .defineInRange("soundVolume", 0.5, 0.0, 1.0);

        BUILDER.comment("Animation Settings");
        ANIMATION_ENABLED = BUILDER
                .comment("Whether to play the crosshair animation when placing a waypoint")
                .define("animationEnabled", true);

        BUILDER.comment("Scroll Zoom Settings");
        SCROLL_ZOOM_ENABLED = BUILDER
                .comment("Whether to enable scroll wheel zoom adjustment while using the spyglass")
                .define("scrollZoomEnabled", true);
        SCROLL_ZOOM_MIN = BUILDER
                .comment("Minimum zoom multiplier. Must be >= 1")
                .defineInRange("scrollZoomMin", 1.0, 1.0, 64.0);
        SCROLL_ZOOM_MAX = BUILDER
                .comment("Maximum zoom multiplier. Must be >= scrollZoomMin")
                .defineInRange("scrollZoomMax", 40.0, 1.0, 64.0);

        SPEC = BUILDER.build();
    }

    public static final ModConfigSpec SPEC;
}