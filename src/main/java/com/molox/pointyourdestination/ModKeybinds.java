package com.molox.pointyourdestination;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public class ModKeybinds {
    public static final String CATEGORY = "key.categories.point_your_destination";

    public static final KeyMapping MARK_WAYPOINT = new KeyMapping(
            "key.point_your_destination.mark_waypoint",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_LEFT,
            CATEGORY
    );
    public static final KeyMapping AUTO_ZOOM = new KeyMapping(
            "key.point_your_destination.auto_zoom",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            InputConstants.MOUSE_BUTTON_MIDDLE,
            CATEGORY
    );
}