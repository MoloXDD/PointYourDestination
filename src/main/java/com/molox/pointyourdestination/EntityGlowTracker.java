package com.molox.pointyourdestination;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class EntityGlowTracker {
    private static final int GLOW_DURATION_TICKS = 1200;
    private static final Map<Integer, Integer> glowingEntities = new HashMap<>();

    @Nullable
    private static LivingEntity aimedEntity = null;
    private static int previewEntityId = -1;

    @Nullable
    public static LivingEntity getAimedEntity() {
        return aimedEntity;
    }

    public static void markEntity(Entity entity) {
        int id = entity.getId();
        int remaining = glowingEntities.getOrDefault(id, 0);
        if (remaining >= GLOW_DURATION_TICKS) return;
        glowingEntities.put(id, GLOW_DURATION_TICKS);
    }

    public static void unmarkEntity(Entity entity) {
        glowingEntities.remove(entity.getId());
    }

    public static boolean isTracked(int entityId) {
        return glowingEntities.containsKey(entityId) || entityId == previewEntityId;
    }

    public static boolean isMarked(int entityId) {
        return glowingEntities.containsKey(entityId);
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            aimedEntity = null;
            previewEntityId = -1;
            return;
        }

        if (mc.player.isUsingItem() && mc.player.getUseItem().is(Items.SPYGLASS)
                && Config.ENTITY_MARK_ENABLED.get()) {
            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 lookVec = mc.player.getLookAngle();
            Vec3 farPos = eyePos.add(lookVec.scale(1024.0));
            AABB searchBox = mc.player.getBoundingBox()
                    .expandTowards(lookVec.scale(1024.0)).inflate(1.0);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    mc.player, eyePos, farPos, searchBox,
                    e -> e != mc.player && e instanceof LivingEntity && e.isAlive(),
                    1024.0 * 1024.0
            );
            if (entityHit != null) {
                Vec3 entityPos = entityHit.getLocation();
                ClipContext clipCtx = new ClipContext(eyePos, entityPos,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
                BlockHitResult blockHit = mc.level.clip(clipCtx);
                if (blockHit.getType() != HitResult.Type.MISS) {
                    entityHit = null;
                }
            }
            aimedEntity = entityHit != null ? (LivingEntity) entityHit.getEntity() : null;
            previewEntityId = aimedEntity != null ? aimedEntity.getId() : -1;
        } else {
            aimedEntity = null;
            previewEntityId = -1;
        }

        Iterator<Map.Entry<Integer, Integer>> it = glowingEntities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Integer> entry = it.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                it.remove();
            } else {
                entry.setValue(remaining);
            }
        }
    }
}