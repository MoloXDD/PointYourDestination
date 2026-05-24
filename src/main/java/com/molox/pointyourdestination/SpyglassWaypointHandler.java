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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import xaero.common.XaeroMinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.common.minimap.waypoints.WaypointSet;
import xaero.common.minimap.waypoints.WaypointVisibilityType;
import xaero.common.minimap.waypoints.WaypointsManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpyglassWaypointHandler {
    private static final int RAYCAST_DISTANCE = 65536;
    private static boolean wasMarkKeyDown = false;
    private static final AtomicBoolean dhRaycastPending = new AtomicBoolean(false);
    private static final Random RANDOM = new Random();
    private static IDhApiTerrainDataCache sharedCache = null;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (!player.isUsingItem() || !player.getUseItem().is(Items.SPYGLASS)) {
            wasMarkKeyDown = false;
            return;
        }

        boolean isMarkKeyDown = ModKeybinds.MARK_WAYPOINT.isDown();
        boolean triggered = isMarkKeyDown && !wasMarkKeyDown;
        wasMarkKeyDown = isMarkKeyDown;
        if (!triggered) return;
        if (dhRaycastPending.get()) return;

        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        if (Config.ENTITY_MARK_ENABLED.get()) {
            Vec3 farPos = eyePos.add(lookVec.scale(RAYCAST_DISTANCE));
            AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(RAYCAST_DISTANCE)).inflate(1.0);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    player, eyePos, farPos, searchBox,
                    e -> e != player && e instanceof LivingEntity && e.isAlive(),
                    RAYCAST_DISTANCE * RAYCAST_DISTANCE
            );
            if (entityHit != null) {
                Vec3 entityPos = entityHit.getLocation();
                ClipContext clipCtx = new ClipContext(eyePos, entityPos,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
                BlockHitResult blockHit = mc.level.clip(clipCtx);
                if (blockHit.getType() != HitResult.Type.MISS) {
                    entityHit = null;
                }
            }
            if (entityHit != null) {
                Entity target = entityHit.getEntity();
                if (EntityGlowTracker.isMarked(target.getId())) {
                    EntityGlowTracker.unmarkEntity(target);
                    if (Config.ANIMATION_ENABLED.get()) {
                        CrosshairAnimationRenderer.startAnimation();
                    }
                } else {
                    EntityGlowTracker.markEntity(target);
                    if (Config.ANIMATION_ENABLED.get()) {
                        CrosshairAnimationRenderer.startAnimation();
                    }
                    playMarkSound(mc, player, eyePos, target.position());
                }
                return;
            }
        }

        Vec3 farPos = eyePos.add(lookVec.scale(RAYCAST_DISTANCE));
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

        if (sharedCache == null) {
            sharedCache = terrainRepo.createSoftCache();
        }

        final IDhApiLevelWrapper finalLevelWrapper = levelWrapper;
        final Vec3 finalEyePos = eyePos;
        final Vec3 finalLookVec = lookVec;
        final IDhApiTerrainDataCache cache = sharedCache;
        dhRaycastPending.set(true);

        Thread.ofPlatform().daemon(true).name("pyd-dh-raycast").start(() -> {
            try {
                DhApiResult result = terrainRepo.raycast(
                        finalLevelWrapper,
                        finalEyePos.x, finalEyePos.y, finalEyePos.z,
                        (float) finalLookVec.x, (float) finalLookVec.y, (float) finalLookVec.z,
                        RAYCAST_DISTANCE,
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

    private static void playMarkSound(Minecraft mc, LocalPlayer player, Vec3 eyePos, Vec3 targetPos) {
        if (mc.level == null) return;
        Vec3 toTarget = targetPos.subtract(eyePos);
        double actualDist = toTarget.length();
        double SOUND_DISTANCE = 8.0;
        Vec3 soundPos = actualDist <= SOUND_DISTANCE
                ? targetPos
                : eyePos.add(toTarget.normalize().scale(SOUND_DISTANCE));
        mc.level.playLocalSound(
                soundPos.x, soundPos.y, soundPos.z,
                ModSounds.PIN.get(),
                SoundSource.MASTER,
                (float) Config.SOUND_VOLUME.get().doubleValue(),
                1.0f, false
        );
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
        Set<Integer> usedIndices = new HashSet<>();
        for (Object obj : waypointSet.getList()) {
            String name = ((Waypoint) obj).getName();
            String prefix = name.startsWith("Destination ") ? "Destination " :
                    name.startsWith("目的地 ") ? "目的地 " : null;
            if (prefix == null) continue;
            try {
                usedIndices.add(Integer.parseInt(name.substring(prefix.length())));
            } catch (NumberFormatException ignored) {}
        }
        int index = 1;
        while (usedIndices.contains(index)) index++;
        return index;
    }

    private static void showInvalidMessage(Minecraft mc) {
        mc.gui.setOverlayMessage(
                Component.translatable("point_your_destination.message.invalid_location"),
                false
        );
    }
}