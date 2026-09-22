package zeldaswordskills_remastered.mixin;

import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zeldaswordskills_remastered.combat.ParryEffectProtection;

@Mixin(Level.class)
public abstract class LevelEffectSourceMixin {
    @Redirect(method = "guardEntityTick", at = @At(value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V", remap = false))
    private void zss$trackEffectSource(Consumer<Entity> action, Object entity) {
        ParryEffectProtection.tick((Entity) entity, action);
    }
}
