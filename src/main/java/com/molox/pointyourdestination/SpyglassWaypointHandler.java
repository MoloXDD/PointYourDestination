package com.molox.pointyourdestination;

import java.util.Random;
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
    private static final AtomicBoolean dhRaycastPending = new AtomicBoolean(false);
    private static final Random RANDOM = new Random();

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
            placeWaypoint(mc, player, eyePos, x, y, z);
            return;
        }

        IDhApiWorldProxy worldProxy = DhApi.Delayed.worldProxy;
        IDhApiTerrainDataRepo terrainRepo = DhApi.Delayed.terrainRepo;

        if (worldProxy == null || terrainRepo == null || !worldProxy.worldLoaded()) {
            showInvalidMessage(mc);
            return;
        }

        IDhApiLevelWrapper levelWrapper = worldProxy.getSinglePlayerLevel();
        if (levelWrapper == null) {
            String dimName = player.level().dimension().location().toString();
            for (Object obj : worldProxy.getAllLoadedLevelsWithDimensionNameLike(dimName)) {
                levelWrapper = (IDhApiLevelWrapper) obj;
                break;
            }
        }

        if (levelWrapper == null) {
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
                mc.execute(() -> {
                    dhRaycastPending.set(false);
                    if (result.success && result.payload != null) {
                        DhApiRaycastResult raycastResult = (DhApiRaycastResult) result.payload;
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
        if (session == null) return;
        WaypointsManager manager = session.getWaypointsManager();
        if (manager == null) return;
        WaypointSet waypointSet = manager.getWaypoints();
        if (waypointSet == null) return;

        int nextIndex = findNextIndex(waypointSet);
        String lang = Minecraft.getInstance().options.languageCode;
        String waypointName = lang.startsWith("zh") ? ("目的地 " + nextIndex) : ("Destination " + nextIndex);

        int colorIndex = RANDOM.nextInt(16);
        Waypoint waypoint = new Waypoint(targetX, targetY, targetZ, waypointName, "★", colorIndex, 3);
        waypoint.setVisibility(WaypointVisibilityType.GLOBAL);
        waypointSet.getList().add(waypoint);

        if (Config.ANIMATION_ENABLED.get()) {
            CrosshairAnimationRenderer.startAnimation();
        }

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
                (float) Config.SOUND_VOLUME.get().doubleValue(),
                1.0f, false
        );
    }

    private static int findNextIndex(WaypointSet waypointSet) {
        java.util.Set<Integer> usedIndices = new java.util.HashSet<>();
        String enTemplate = "Destination %d";
        String zhTemplate = "目的地 %d";
        for (Object obj : waypointSet.getList()) {
            Waypoint wp = (Waypoint) obj;
            String name = wp.getName();
            for (int i = 1; i <= waypointSet.getList().size() + 1; i++) {
                if (name.equals(String.format(enTemplate, i)) || name.equals(String.format(zhTemplate, i))) {
                    usedIndices.add(i);
                    break;
                }
            }
        }
        int index = 1;
        while (usedIndices.contains(index)) {
            index++;
        }
        return index;
    }

    private static void showInvalidMessage(Minecraft mc) {
        mc.gui.setOverlayMessage(
                Component.translatable("point_your_destination.message.invalid_location"),
                false
        );
    }
}