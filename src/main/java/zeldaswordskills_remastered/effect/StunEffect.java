package zeldaswordskills_remastered.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class StunEffect extends MobEffect {
    private static final String MOVEMENT_MODIFIER_ID = "6026a8b9-8d5f-4f76-9067-2b69ce4ebf56";
    private static final String ATTACK_SPEED_MODIFIER_ID = "bfcf2cf1-73c5-4a3a-aa8b-f92f47b77ec7";

    public StunEffect() {
        super(MobEffectCategory.HARMFUL, 0xD7E6FF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, MOVEMENT_MODIFIER_ID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
        addAttributeModifier(Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER_ID, -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
