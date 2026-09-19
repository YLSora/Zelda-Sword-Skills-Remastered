package zeldaswordskills_remastered.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import zeldaswordskills_remastered.config.ZSSConfig;

import java.util.UUID;

/** Server-side rules controlled by the single Master Mode config switch. */
public final class MasterMode {
    public static final UUID PLAYER_HEALTH_MODIFIER_ID = UUID.fromString("87743e08-401b-4e59-9c20-e2074706842d");
    public static final UUID ENEMY_HEALTH_MODIFIER_ID = UUID.fromString("21b71f40-a029-4d6d-b24e-8dc526d6808c");
    private static final double PLAYER_HEALTH_REDUCTION = -10.0D;

    private MasterMode() {
    }

    /** Five base hearts, before permanent Heart Containers are added. */
    public static void updatePlayerHealth(ServerPlayer player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        boolean enabled = ZSSConfig.SERVER.masterMode.get();
        AttributeModifier existing = health.getModifier(PLAYER_HEALTH_MODIFIER_ID);
        if (enabled && existing != null && existing.getAmount() == PLAYER_HEALTH_REDUCTION) return;
        if (!enabled && existing == null) return;

        float oldMaximum = player.getMaxHealth();
        if (existing != null) health.removeModifier(PLAYER_HEALTH_MODIFIER_ID);
        if (enabled) {
            health.addTransientModifier(new AttributeModifier(PLAYER_HEALTH_MODIFIER_ID,
                    "Master Mode base health", PLAYER_HEALTH_REDUCTION, AttributeModifier.Operation.ADDITION));
        }
        float gained = player.getMaxHealth() - oldMaximum;
        if (gained > 0.0F) player.heal(gained);
        else if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Doubles both maximum and current health while preserving the enemy's health percentage. */
    public static void updateEnemyHealth(LivingEntity enemy) {
        if (!isEnemy(enemy)) return;
        AttributeInstance health = enemy.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        boolean enabled = ZSSConfig.SERVER.masterMode.get();
        AttributeModifier existing = health.getModifier(ENEMY_HEALTH_MODIFIER_ID);
        if (enabled && existing != null && existing.getAmount() == 1.0D) return;
        if (!enabled && existing == null) return;

        float oldMaximum = enemy.getMaxHealth();
        float ratio = oldMaximum > 0.0F ? Mth.clamp(enemy.getHealth() / oldMaximum, 0.0F, 1.0F) : 1.0F;
        if (existing != null) health.removeModifier(ENEMY_HEALTH_MODIFIER_ID);
        if (enabled) {
            health.addTransientModifier(new AttributeModifier(ENEMY_HEALTH_MODIFIER_ID,
                    "Master Mode enemy health", 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        enemy.setHealth(enemy.getMaxHealth() * ratio);
    }

    public static float enemyDamage(float amount, Entity attacker) {
        if (!ZSSConfig.SERVER.masterMode.get() || !(attacker instanceof LivingEntity living) || !isEnemy(living)) {
            return amount;
        }
        return Math.min(Float.MAX_VALUE, amount * 2.0F);
    }

    /** Vanilla hostile mobs use Enemy; ZSS hostile entities use the monster category. */
    public static boolean isEnemy(LivingEntity entity) {
        return entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
    }
}
