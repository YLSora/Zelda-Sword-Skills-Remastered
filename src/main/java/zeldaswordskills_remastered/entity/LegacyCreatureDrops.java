package zeldaswordskills_remastered.entity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Context-sensitive one-time whip loot and the boss reward stack. */
public final class LegacyCreatureDrops {
    private LegacyCreatureDrops() {}

    static ItemStack whipLoot(LegacyCreature creature) {
        return switch (creature.kind()) {
            case DARKNUT_STANDARD, DARKNUT_MIGHTY, DARKNUT_BOSS ->
                    creature instanceof DarknutCreature darknut && darknut.armorRemaining() <= 0
                            ? new ItemStack(ZSSRegistries.getItem("knights_crest")) : ItemStack.EMPTY;
            case CHU_RED -> new ItemStack(ZSSRegistries.getItem("chu_jelly_red"));
            case CHU_GREEN -> new ItemStack(ZSSRegistries.getItem("chu_jelly_green"));
            case CHU_BLUE -> new ItemStack(ZSSRegistries.getItem("chu_jelly_blue"));
            case CHU_YELLOW -> new ItemStack(ZSSRegistries.getItem("chu_jelly_yellow"));
            case KEESE_NORMAL, KEESE_FIRE, KEESE_ICE, KEESE_THUNDER -> new ItemStack(ZSSRegistries.getItem("monster_claw"));
            case KEESE_CURSED -> new ItemStack(ZSSRegistries.getItem("evil_crystal"));
            case OCTOROK_NORMAL, OCTOROK_BOMB, WATER_BOSS -> new ItemStack(ZSSRegistries.getItem("tentacle"));
            case SKULLTULA_NORMAL -> creature.getRandom().nextInt(5) == 0 ? new ItemStack(Items.EMERALD) : ItemStack.EMPTY;
            case SKULLTULA_GOLD -> new ItemStack(ZSSRegistries.getItem("skulltula_token"));
            default -> ItemStack.EMPTY;
        };
    }

    public static ItemStack bonusHeartOrb() {
        ItemStack orb = new ItemStack(ZSSRegistries.getItem("skill_orb"));
        orb.getOrCreateTag().putString(ProgressionItem.SKILL_TAG, ZSSContentIds.BONUS_HEART.toString());
        return orb;
    }
}
