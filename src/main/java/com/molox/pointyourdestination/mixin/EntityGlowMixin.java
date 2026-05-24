package com.molox.pointyourdestination.mixin;

import com.molox.pointyourdestination.Config;
import com.molox.pointyourdestination.EntityGlowTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityGlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void onIsCurrentlyGlowing(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && Config.ENTITY_MARK_ENABLED.get() && EntityGlowTracker.isTracked(self.getId())) {
            cir.setReturnValue(true);
        }
    }
}