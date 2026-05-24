package com.molox.pointyourdestination;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public class ScrollZoomHandler {
    // 目标方块占屏幕高度的比例，调大则方块显示更大（缩放更强）
    private static final double AUTO_ZOOM_BLOCK_SCREEN_FRACTION = 0.15;

    private static int zoomLevel = 10;
    private static double smoothZoomLevel = 1.0;
    private static long lastRenderTime = -1;
    private static boolean wasUsingSpyglass = false;

    private static boolean autoZoomActive = false;
    private static boolean wasAutoZoomKeyDown = false;
    private static boolean scrollLocked = false;
    private static double autoZoomStartSmooth = 1.0;
    private static double autoZoomTarget = 10.0;
    private static long autoZoomStartTime = -1;
    private static final long AUTO_ZOOM_ANIM_MS = 300;

    @SubscribeEvent
    public void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!Config.SCROLL_ZOOM_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.isUsingItem() || !mc.player.getUseItem().is(Items.SPYGLASS)) return;

        if (scrollLocked) {
            event.setCanceled(true);
            return;
        }

        int min = (int) Math.round(Config.SCROLL_ZOOM_MIN.get());
        int max = (int) Math.round(Config.SCROLL_ZOOM_MAX.get());
        int step = (int) Math.max(1, Math.round(zoomLevel * 0.15));

        zoomLevel = Mth.clamp(zoomLevel + (int) Math.signum(event.getScrollDeltaY()) * step, min, max);
        autoZoomActive = false;

        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onComputeFov(ViewportEvent.ComputeFov event) {
        if (!Config.SCROLL_ZOOM_ENABLED.get()) return;
        if (!event.usedConfiguredFov()) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        boolean usingSpyglass = player.isUsingItem() && player.getUseItem().is(Items.SPYGLASS);

        if (!usingSpyglass) {
            lastRenderTime = -1;
            wasUsingSpyglass = false;
            wasAutoZoomKeyDown = false;
            scrollLocked = false;
            autoZoomActive = false;
            return;
        }

        // 自动缩放键检测
        boolean isAutoZoomKeyDown = ModKeybinds.AUTO_ZOOM.isDown();
        if (isAutoZoomKeyDown && !wasAutoZoomKeyDown) {
            triggerAutoZoom(mc, player);
        }
        wasAutoZoomKeyDown = isAutoZoomKeyDown;

        int min = (int) Math.round(Config.SCROLL_ZOOM_MIN.get());
        int max = (int) Math.round(Config.SCROLL_ZOOM_MAX.get());

        long now = System.currentTimeMillis();

        if (!wasUsingSpyglass) {
            smoothZoomLevel = 1.0;
            lastRenderTime = now;
            wasUsingSpyglass = true;
        }

        if (autoZoomActive) {
            long elapsed = now - autoZoomStartTime;
            if (elapsed >= AUTO_ZOOM_ANIM_MS) {
                smoothZoomLevel = autoZoomTarget;
                zoomLevel = (int) Math.round(autoZoomTarget);
                scrollLocked = false;
                autoZoomActive = false;
            } else {
                double t = (double) elapsed / AUTO_ZOOM_ANIM_MS;
                double ease = (1 - Math.cos(t * Math.PI)) / 2.0;
                double logStart = Math.log(autoZoomStartSmooth);
                double logTarget = Math.log(autoZoomTarget);
                smoothZoomLevel = Math.exp(logStart + (logTarget - logStart) * ease);
            }
        } else {
            zoomLevel = Mth.clamp(zoomLevel, min, max);
            double deltaSeconds = (now - lastRenderTime) / 1000.0;
            double logSmooth = Math.log(smoothZoomLevel);
            double logTarget = Math.log(zoomLevel);
            double factor = 1.0 - Math.pow(0.001, deltaSeconds);
            smoothZoomLevel = Math.exp(logSmooth + (logTarget - logSmooth) * factor);
        }

        lastRenderTime = now;

        double baseFov = mc.options.fov().get().intValue();
        event.setFOV(baseFov / smoothZoomLevel);
    }

    @SubscribeEvent
    public void onCalculatePlayerTurn(CalculatePlayerTurnEvent event) {
        if (!Config.SCROLL_ZOOM_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.isUsingItem() || !mc.player.getUseItem().is(Items.SPYGLASS)) return;
        if (smoothZoomLevel <= 0) return;

        double s = event.getMouseSensitivity();
        double d2 = s * 0.6 + 0.2;
        double d3 = d2 * d2 * d2;
        double targetD3 = d3 * (6.0 / smoothZoomLevel);
        double targetD2 = Math.cbrt(targetD3);
        double targetSensitivity = (targetD2 - 0.2) / 0.6;
        event.setMouseSensitivity(targetSensitivity);
    }

    private static void triggerAutoZoom(Minecraft mc, LocalPlayer player) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 farPos = eyePos.add(lookVec.scale(1024.0));
        ClipContext ctx = new ClipContext(eyePos, farPos,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = mc.level.clip(ctx);

        double targetZoom;
        if (hit.getType() != HitResult.Type.MISS) {
            double distance = eyePos.distanceTo(Vec3.atCenterOf(hit.getBlockPos()));
            double baseFov = mc.options.fov().get().intValue();
            double fovRad = Math.toRadians(baseFov);
            // 距离为 distance 时，1格方块对应的视角：
            // angularSize = 2 * atan(0.5 / distance)
            // 目标FOV = angularSize / AUTO_ZOOM_BLOCK_SCREEN_FRACTION
            // zoom = baseFov / targetFov
            double angularSizeDeg = Math.toDegrees(2.0 * Math.atan(0.5 / distance));
            double targetFov = angularSizeDeg / AUTO_ZOOM_BLOCK_SCREEN_FRACTION;
            targetZoom = baseFov / targetFov;
        } else {
            targetZoom = 10.0;
        }

        int min = (int) Math.round(Config.SCROLL_ZOOM_MIN.get());
        int max = (int) Math.round(Config.SCROLL_ZOOM_MAX.get());
        targetZoom = Mth.clamp(targetZoom, min, max);

        autoZoomStartSmooth = smoothZoomLevel;
        autoZoomTarget = targetZoom;
        autoZoomStartTime = System.currentTimeMillis();
        autoZoomActive = true;
        scrollLocked = true;
    }
}