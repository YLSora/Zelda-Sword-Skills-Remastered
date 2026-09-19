package zeldaswordskills_remastered.worldgen;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.List;

public final class DungeonBossEquipment {
    private DungeonBossEquipment() {}

    public static void equipArmor(Mob mob, int difficulty) {
        var armor = switch (difficulty) {
            case 1 -> List.of(Items.CHAINMAIL_BOOTS, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_HELMET);
            case 2 -> List.of(Items.IRON_BOOTS, Items.IRON_LEGGINGS, Items.IRON_CHESTPLATE, Items.IRON_HELMET);
            default -> List.of(Items.DIAMOND_BOOTS, Items.DIAMOND_LEGGINGS, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_HELMET);
        };
        var slots = List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD);
        for (int i = 0; i < slots.size(); i++) mob.setItemSlot(slots.get(i),
                EnchantmentHelper.enchantItem(mob.getRandom(), new ItemStack(armor.get(i)),
                        difficulty + mob.getRandom().nextInt(difficulty * 5), false));
    }
}
