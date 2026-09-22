package zeldaswordskills_remastered.mixin;

import javax.annotation.Nullable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zeldaswordskills_remastered.combat.ParryEffectProtection;

@Mixin(LivingEntity.class)
public abstract class LivingEffectSourceMixin {
    @Redirect(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean zss$checkEffectSource(LivingEntity target, MobEffectInstance effect,
                                         MobEffectInstance requested, @Nullable Entity source) {
        return ParryEffectProtection.canApply(target, effect, source);
    }
}
