package zeldaswordskills_remastered.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class StunEffect extends MobEffect {
    private static final String MOVEMENT_MODIFIER_ID = "6026a8b9-8d5f-4f76-9067-2b69ce4ebf56";
    private static final String ATTACK_SPEED_MODIFIER_ID = "bfcf2cf1-73c5-4a3a-aa8b-f92f47b77ec7";
    private static final String ENDED_AT = "zss_stun_ended_at";

    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0xD7E6FF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, MOVEMENT_MODIFIER_ID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_ID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public void removeAttributeModifiers(LivingEntity entity, AttributeMap attributes, int amplifier) {
        super.removeAttributeModifiers(entity, attributes, amplifier);
        // Both natural expiry and early removal pass through here, including same-tick removal.
        if (!entity.level().isClientSide) entity.getPersistentData().putLong(ENDED_AT, entity.level().getGameTime());
    }

    public static boolean wasStunnedWithin(LivingEntity entity, int ticks) {
        if (entity.hasEffect(ZSSRegistries.STUN.get())) return true;
        var history = entity.getPersistentData();
        return history.contains(ENDED_AT) && entity.level().getGameTime() - history.getLong(ENDED_AT) < ticks;
    }
}
