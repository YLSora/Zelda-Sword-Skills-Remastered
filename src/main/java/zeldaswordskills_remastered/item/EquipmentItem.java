package zeldaswordskills_remastered.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

import java.util.UUID;

/** Equippable Zelda armor. Runtime effects are centralized in ZSSItemEvents. */
public final class EquipmentItem extends ArmorItem {
    private final Gear gear;

    public EquipmentItem(ArmorMaterial material, Type type, Gear gear, Properties properties) {
        super(material, type, properties);
        this.gear = gear;
    }

    public Gear gear() { return gear; }

    @Override public boolean canBeDepleted() { return false; }
    @Override public boolean isEnchantable(ItemStack stack) { return true; }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(slot, stack);
        if (slot != EquipmentSlot.FEET && slot != EquipmentSlot.HEAD) return base;
        ImmutableMultimap.Builder<Attribute, AttributeModifier> modifiers = ImmutableMultimap.builder();
        modifiers.putAll(base);
        if (gear == Gear.HEAVY_BOOTS) {
            modifiers.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(UUID.fromString("b6c8ccb6-ae7b-4f14-908a-2f41bdb4d720"),
                    "Heavy Boots movement", -0.6D, AttributeModifier.Operation.MULTIPLY_TOTAL));
            modifiers.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(UUID.fromString("71af0f88-82e5-49de-b9cc-844048e33d69"),
                    "Heavy Boots weight", 1.0D, AttributeModifier.Operation.ADDITION));
        } else if (gear == Gear.PEGASUS_BOOTS) {
            modifiers.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(UUID.fromString("36a0fc05-50eb-460b-8961-615633a6d813"),
                    "Pegasus Boots speed", 0.3D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        } else if (gear == Gear.BUNNY_HOOD) {
            modifiers.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(UUID.fromString("8412c9f7-9645-4c24-8fd1-6efb8282e822"),
                    "Bunny Hood speed", 0.3D, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        return modifiers.build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (gear != Gear.BLAST_MASK || player.getCooldowns().isOnCooldown(this)) return super.use(level, player, hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            level.explode(serverPlayer, player.getX(), player.getY() + 1.0D, player.getZ(), 3.0F, false, Level.ExplosionInteraction.MOB);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Nullable
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        String texture = gear.texture();
        if (texture == null) return null;
        int layer = slot == EquipmentSlot.LEGS ? 2 : 1;
        return ZeldaSwordSkills_Remastered.MOD_ID + ":textures/armor/" + texture + "_layer_" + layer + ".png";
    }

    public enum Gear {
        HERO("hero_tunic"), GORON("goron_tunic"), ZORA("zora_tunic"),
        HEAVY_BOOTS(null), HOVER_BOOTS(null), PEGASUS_BOOTS(null), RUBBER_BOOTS("boots_rubber"),
        BLAST_MASK("mask_blast"), BUNNY_HOOD("mask_bunny"), COUPLES_MASK("mask_couples"),
        GERUDO_MASK("mask_gerudo"), GIANTS_MASK("mask_giants"), GIBDO_MASK("mask_gibdo"),
        HAWKEYE_MASK("mask_hawkeye"), KEATON_MASK("mask_keaton"), MASK_OF_SCENTS("mask_scents"),
        SKULL_MASK("mask_skull"), SPOOKY_MASK("mask_spooky"), STONE_MASK("mask_stone"),
        MASK_OF_TRUTH("mask_truth"), DEKU_MASK("mask_deku"), GORON_MASK("mask_goron"),
        ZORA_MASK("mask_zora"), FIERCE_DEITY_MASK("mask_fierce"), MAJORA_MASK("mask_majora");

        private final String texture;
        Gear(String texture) { this.texture = texture; }
        public String texture() { return texture; }
    }
}
