package com.molox.pointyourdestination;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataCache;
import com.seibel.distanthorizons.api.interfaces.data.IDhApiTerrainDataRepo;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiWorldProxy;
import com.seibel.distanthorizons.api.objects.DhApiResult;
import com.seibel.distanthorizons.api.objects.data.DhApiRaycastResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.WaypointSet;
import xaero.common.minimap.waypoints.WaypointVisibilityType;
import xaero.common.minimap.waypoints.WaypointsManager;

import java.util.concurrent.atomic.AtomicBoolean;

public class SpyglassWaypointHandler {
    private static boolean wasAttackDown = false;
    private static int waypointCounter = 1;
    private static final AtomicBoolean dhRaycastPending = new AtomicBoolean(false);

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!player.isUsingItem() || !player.getUseItem().is(Items.SPYGLASS)) {
            wasAttackDown = false;
            return;
        }

        boolean isAttackDown = mc.options.keyAttack.isDown();
        boolean triggered = isAttackDown && !wasAttackDown;
        wasAttackDown = isAttackDown;
        if (!triggered) return;
        if (dhRaycastPending.get()) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 farPos = eyePos.add(lookVec.scale(1024.0));
        ClipContext ctx = new ClipContext(eyePos, farPos,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult hit = mc.level.clip(ctx);

        if (hit.getType() != HitResult.Type.MISS) {
            int x = hit.getBlockPos().getX();
            int y = hit.getBlockPos().getY();
            int z = hit.getBlockPos().getZ();
            PointYourDestination.LOGGER.debug("Vanilla raycast hit: {},{},{}", x, y, z);
            placeWaypoint(mc, player, eyePos, x, y, z);
            return;
        }

        PointYourDestination.LOGGER.debug("Vanilla raycast miss, trying DH");

        IDhApiWorldProxy worldProxy = DhApi.Delayed.worldProxy;
        IDhApiTerrainDataRepo terrainRepo = DhApi.Delayed.terrainRepo;

        PointYourDestination.LOGGER.debug("worldProxy={}, terrainRepo={}", worldProxy, terrainRepo);

        if (worldProxy == null || terrainRepo == null) {
            PointYourDestination.LOGGER.debug("DH not ready, showing invalid");
            showInvalidMessage(mc);
            return;
        }

        PointYourDestination.LOGGER.debug("worldProxy.worldLoaded()={}", worldProxy.worldLoaded());

        if (!worldProxy.worldLoaded()) {
            showInvalidMessage(mc);
            return;
        }

        IDhApiLevelWrapper levelWrapper = worldProxy.getSinglePlayerLevel();
        PointYourDestination.LOGGER.debug("getSinglePlayerLevel()={}", levelWrapper);

        if (levelWrapper == null) {
            String dimName = player.level().dimension().location().toString();
            PointYourDestination.LOGGER.debug("Trying dimension match: {}", dimName);
            for (Object obj : worldProxy.getAllLoadedLevelsWithDimensionNameLike(dimName)) {
                levelWrapper = (IDhApiLevelWrapper) obj;
                PointYourDestination.LOGGER.debug("Found level via dimension: {}", levelWrapper);
                break;
            }
        }

        if (levelWrapper == null) {
            PointYourDestination.LOGGER.debug("No level found, showing invalid");
            showInvalidMessage(mc);
            return;
        }

        final IDhApiLevelWrapper finalLevelWrapper = levelWrapper;
        final Vec3 finalEyePos = eyePos;
        final Vec3 finalLookVec = lookVec;
        dhRaycastPending.set(true);

        Thread.ofVirtual().start(() -> {
            try {
                IDhApiTerrainDataCache cache = terrainRepo.createSoftCache();
                DhApiResult result = terrainRepo.raycast(
                        finalLevelWrapper,
                        finalEyePos.x, finalEyePos.y, finalEyePos.z,
                        (float) finalLookVec.x, (float) finalLookVec.y, (float) finalLookVec.z,
                        1024,
                        cache
                );
                PointYourDestination.LOGGER.debug("DH raycast result: success={}, payload={}", result.success, result.payload);
                mc.execute(() -> {
                    dhRaycastPending.set(false);
                    if (result.success && result.payload != null) {
                        DhApiRaycastResult raycastResult = (DhApiRaycastResult) result.payload;
                        PointYourDestination.LOGGER.debug("DH raycast pos: {},{},{}, topY={}",
                                raycastResult.pos.x, raycastResult.pos.y, raycastResult.pos.z,
                                raycastResult.dataPoint.topYBlockPos);
                        placeWaypoint(mc, mc.player, finalEyePos,
                                raycastResult.pos.x, raycastResult.dataPoint.topYBlockPos, raycastResult.pos.z);
                    } else {
                        showInvalidMessage(mc);
                    }
                });
            } catch (Exception e) {
                PointYourDestination.LOGGER.error("DH raycast exception: ", e);
                mc.execute(() -> {
                    dhRaycastPending.set(false);
                    showInvalidMessage(mc);
                });
            }
        });
    }

    private static void placeWaypoint(Minecraft mc, LocalPlayer player, Vec3 eyePos,
                                      int targetX, int targetY, int targetZ) {
        XaeroMinimapSession session = XaeroMinimapSession.getCurrentSession();
        if (session == null) {
            PointYourDestination.LOGGER.debug("XaeroMinimapSession is null");
            return;
        }
        WaypointsManager manager = session.getWaypointsManager();
        if (manager == null) {
            PointYourDestination.LOGGER.debug("WaypointsManager is null");
            return;
        }
        WaypointSet waypointSet = manager.getWaypoints();
        if (waypointSet == null) {
            PointYourDestination.LOGGER.debug("WaypointSet is null");
            return;
        }

        String waypointName = "目的地 " + waypointCounter;
        int colorIndex = new java.util.Random().nextInt(16);
        Waypoint waypoint = new Waypoint(targetX, targetY, targetZ, waypointName, "★", colorIndex, 3);
        waypoint.setVisibility(WaypointVisibilityType.GLOBAL);
        waypointSet.getList().add(waypoint);
        waypointCounter++;

        PointYourDestination.LOGGER.debug("Waypoint placed: {} at {},{},{}", waypointName, targetX, targetY, targetZ);

        CrosshairAnimationRenderer.startAnimation();

        if (player == null || mc.level == null) return;
        Vec3 toTarget = new Vec3(targetX - eyePos.x, targetY - eyePos.y, targetZ - eyePos.z);
        double actualDist = toTarget.length();
        double SOUND_DISTANCE = 8.0;
        Vec3 soundPos = actualDist <= SOUND_DISTANCE
                ? new Vec3(targetX, targetY, targetZ)
                : eyePos.add(toTarget.normalize().scale(SOUND_DISTANCE));
        mc.level.playLocalSound(
                soundPos.x, soundPos.y, soundPos.z,
                ModSounds.PIN.get(),
                SoundSource.MASTER,
                1.0f, 1.0f, false
        );
    }

    private static void showInvalidMessage(Minecraft mc) {
        mc.gui.setOverlayMessage(
                Component.translatable("point_your_destination.message.invalid_location"),
                false
        );
    }
}