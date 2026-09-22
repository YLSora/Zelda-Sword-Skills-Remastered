package zeldaswordskills_remastered.combat;

import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import zeldaswordskills_remastered.capability.ZSSPlayerData;

/** Supplies the effect source missing from Forge's Applicable event, including source-less mob casts. */
public final class ParryEffectProtection {
    private static final ThreadLocal<Entity> TICKING_ENTITY = new ThreadLocal<>();
    private static final ThreadLocal<Application> APPLICATION = new ThreadLocal<>();

    private record Application(LivingEntity target, @Nullable Entity source) {}

    private ParryEffectProtection() {}

    public static void tick(Entity entity, Consumer<Entity> action) {
        Entity previous = TICKING_ENTITY.get();
        TICKING_ENTITY.set(entity);
        try {
            action.accept(entity);
        } finally {
            if (previous == null) TICKING_ENTITY.remove();
            else TICKING_ENTITY.set(previous);
        }
    }

    public static boolean canApply(LivingEntity target, MobEffectInstance effect, @Nullable Entity source) {
        Application previous = APPLICATION.get();
        APPLICATION.set(new Application(target, source != null ? source : TICKING_ENTITY.get()));
        try {
            return target.canBeAffected(effect);
        } finally {
            if (previous == null) APPLICATION.remove();
            else APPLICATION.set(previous);
        }
    }

    public static boolean blocks(ServerPlayer player, ZSSPlayerData data) {
        Application application = APPLICATION.get();
        return application != null && application.target() == player && application.source() != null
                && data.combat().parriedAttackEffectsImmune(player.level().getGameTime(), application.source().getUUID());
    }
}
