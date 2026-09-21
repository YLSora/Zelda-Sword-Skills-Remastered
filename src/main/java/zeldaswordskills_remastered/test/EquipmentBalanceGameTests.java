package zeldaswordskills_remastered.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.item.EquipmentItem;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EquipmentBalanceGameTests {
    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void toolsConsumeDurabilityOnlyOnSuccessfulSurvivalUse(GameTestHelper helper) {
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ToolDurability"));
        var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
        var feather = new ItemStack(ZSSRegistries.getItem("rocs_feather"));
        player.setItemInHand(hand, feather);
        player.getAbilities().instabuild = false;
        player.setOnGround(false);
        feather.getItem().use(helper.getLevel(), player, hand);
        helper.assertTrue(feather.getDamageValue() == 0, "Failed tool use consumed durability");
        player.setOnGround(true);
        feather.getItem().use(helper.getLevel(), player, hand);
        helper.assertTrue(feather.getDamageValue() == 1, "Successful tool use did not consume one durability");
        player.getCooldowns().removeCooldown(feather.getItem());
        player.getAbilities().instabuild = true;
        feather.getItem().use(helper.getLevel(), player, hand);
        helper.assertTrue(feather.getDamageValue() == 1, "Creative tool use consumed durability");
        player.getAbilities().instabuild = false;
        player.getCooldowns().removeCooldown(feather.getItem());
        feather.setDamageValue(255);
        feather.getItem().use(helper.getLevel(), player, hand);
        helper.assertTrue(feather.isEmpty(), "Tool did not break after its final use");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void equipmentHasRequestedDurability(GameTestHelper helper) {
        String[] ids = {"kokiri_sword", "ordon_sword", "giant_sword", "biggoron_sword", "darknut_sword",
                "wooden_hammer", "skull_hammer", "megaton_hammer", "hero_bow",
                "deku_shield", "hylian_shield", "mirror_shield",
                "din_crystal", "farore_crystal", "nayru_crystal",
                "bombos_medallion", "ether_medallion", "quake_medallion",
                "hookshot", "stoneshot", "multishot", "boomerang", "magic_boomerang",
                "fire_rod", "ice_rod", "tornado_rod", "whip", "magic_whip", "slingshot", "magic_mirror",
                "hero_tunic_helmet", "hero_tunic_chestplate", "hero_tunic_leggings", "hero_tunic_boots"};
        int[] uses = {256, 512, 1200, 2000, 768, 512, 512, 512, 512, 500, 3000, 3000,
                64, 64, 64, 128, 128, 128, 384, 384, 384, 384, 384, 384, 384, 384, 384, 384,
                256, 256, 363, 528, 495, 429};
        for (int i = 0; i < ids.length; i++) {
            ItemStack stack = new ItemStack(ZSSRegistries.getItem(ids[i]));
            helper.assertTrue(stack.isDamageableItem() && stack.getMaxDamage() == uses[i], "Wrong durability: " + ids[i]);
        }
        for (String id : new String[]{"master_sword", "tempered_sword", "golden_sword", "true_master_sword", "blast_mask", "bunny_hood",
                "fairy_ocarina", "ocarina_of_time", "goddess_harp"}) {
            helper.assertTrue(!new ItemStack(ZSSRegistries.getItem(id)).isDamageableItem(), "Item should remain unbreakable: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void weaponsHaveRequestedTotalDamageAndSpeed(GameTestHelper helper) {
        String[] ids = {"ordon_sword", "giant_sword", "biggoron_sword", "master_sword", "tempered_sword",
                "golden_sword", "true_master_sword", "darknut_sword", "wooden_hammer", "skull_hammer", "megaton_hammer"};
        double[] damage = {7, 10, 16, 9, 10, 12, 15, 10, 10, 14, 18};
        double[] speed = {1.6, 1, .9, 1.6, 1.6, 1.6, 1.6, 1.2, .8, .7, .6};
        for (int i = 0; i < ids.length; i++) {
            ItemStack stack = new ItemStack(ZSSRegistries.getItem(ids[i]));
            helper.assertTrue(Math.abs(1 + amount(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE) - damage[i]) < .00001,
                    "Incorrect total attack damage: " + ids[i]);
            helper.assertTrue(Math.abs(4 + amount(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED) - speed[i]) < .00001,
                    "Incorrect total attack speed: " + ids[i]);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void allZssArmorUsesBalancedStatsOnlyInItsSlot(GameTestHelper helper) {
        for (var entry : ZSSRegistries.ITEMS.getEntries()) {
            if (!(entry.get() instanceof EquipmentItem armor)) continue;
            ItemStack stack = new ItemStack(armor);
            EquipmentSlot slot = armor.getEquipmentSlot();
            if (armor.canBeDepleted()) helper.assertTrue(stack.getMaxDamage()
                            == net.minecraft.world.item.ArmorMaterials.DIAMOND.getDurabilityForType(armor.getType()),
                    "Armor must use diamond durability: " + entry.getId());
            int defense = switch (slot) {
                case CHEST -> 8;
                case LEGS -> 5;
                default -> 2;
            };
            helper.assertTrue(armor.getDefense() == defense
                            && amount(stack, slot, Attributes.ARMOR) == defense
                            && amount(stack, slot, Attributes.ARMOR_TOUGHNESS) == 2,
                    "Incorrect armor defense/toughness: " + entry.getId());
            double resistance = armor.gear() == EquipmentItem.Gear.HEAVY_BOOTS ? 1.06 : .06;
            helper.assertTrue(Math.abs(amount(stack, slot, Attributes.KNOCKBACK_RESISTANCE) - resistance) < .00001,
                    "Incorrect armor knockback resistance: " + entry.getId());
            for (EquipmentSlot other : EquipmentSlot.values()) {
                if (other != slot) helper.assertTrue(stack.getAttributeModifiers(other).isEmpty(),
                        "Armor grants attributes from the wrong slot: " + entry.getId());
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void darknutSwordDoesNotAddToVariantAttackPower(GameTestHelper helper) {
        var types = java.util.List.of(ZSSRegistries.DARKNUT, ZSSRegistries.DARKNUT_MIGHTY, ZSSRegistries.DARKNUT_BOSS);
        double[] damage = {10, 12, 15};
        for (int i = 0; i < types.size(); i++) {
            var darknut = types.get(i).get().create(helper.getLevel());
            var victim = EntityType.ZOMBIE.create(helper.getLevel());
            try {
                victim.getAttribute(Attributes.ARMOR).setBaseValue(0);
                victim.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
                victim.setHealth(100);
                // LivingEntity applies held-item modifiers during equipment updates.
                darknut.getAttributes().addTransientAttributeModifiers(
                        darknut.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND));
                helper.assertTrue(darknut.doHurtTarget(victim) && Math.abs(100 - victim.getHealth() - damage[i]) < .00001,
                        "Equipped Darknut dealt incorrect normal-hit damage: " + types.get(i).getId());
            } finally {
                darknut.discard();
                victim.discard();
            }
        }
        helper.succeed();
    }

    private static double amount(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        return stack.getAttributeModifiers(slot).get(attribute).stream().mapToDouble(modifier -> modifier.getAmount()).sum();
    }
}
