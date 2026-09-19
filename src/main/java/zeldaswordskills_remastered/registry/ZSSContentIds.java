package zeldaswordskills_remastered.registry;

import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ZSSContentIds {
    public static final ResourceLocation SWORD_BASIC = id("sword_basic");
    public static final ResourceLocation HELM_SPLITTER = id("helm_splitter");
    public static final ResourceLocation DODGE = id("dodge");
    public static final ResourceLocation LEAPING_BLOW = id("leaping_blow");
    public static final ResourceLocation PARRY = id("parry");
    public static final ResourceLocation DASH = id("dash");
    public static final ResourceLocation SPIN_ATTACK = id("spin_attack");
    public static final ResourceLocation SUPER_SPIN_ATTACK = id("super_spin_attack");
    public static final ResourceLocation SWORD_BEAM = id("sword_beam");
    public static final ResourceLocation SWORD_BREAK = id("sword_break");
    public static final ResourceLocation MORTAL_DRAW = id("mortal_draw");
    public static final ResourceLocation BONUS_HEART = id("bonus_heart");
    public static final ResourceLocation RISING_CUT = id("rising_cut");
    public static final ResourceLocation ENDING_BLOW = id("ending_blow");
    public static final ResourceLocation FLASH_ASSAULT = id("flash_assault");
    /** Added by this port: a second mid-air jump, independent of the Rising Cut lift. */
    public static final ResourceLocation DOUBLE_JUMP = id("double_jump");
    public static final ResourceLocation CONTINUOUS_FLASH = id("continuous_flash");
    public static final ResourceLocation EPONA = id("epona");
    public static final ResourceLocation HEALING = id("healing");
    public static final ResourceLocation SARIA = id("saria");
    public static final ResourceLocation SOARING = id("soaring");
    public static final ResourceLocation STORMS = id("storms");
    public static final ResourceLocation SUN = id("sun");
    public static final ResourceLocation TIME = id("time");
    public static final ResourceLocation BOLERO = id("bolero");
    public static final ResourceLocation MINUET = id("minuet");
    public static final ResourceLocation PRELUDE = id("prelude");
    public static final ResourceLocation OATH = id("oath");
    public static final ResourceLocation NOCTURNE = id("nocturne");
    public static final ResourceLocation REQUIEM = id("requiem");
    public static final ResourceLocation SERENADE = id("serenade");
    public static final ResourceLocation LULLABY = id("lullaby");
    public static final ResourceLocation SCARECROW = id("scarecrow");
    public static final ResourceLocation ZELDA_TALK = id("zelda_talk");
    public static final ResourceLocation PENDANTS = id("pendants");
    public static final ResourceLocation ZELDA_LETTER = id("zelda_letter");
    public static final ResourceLocation MASK_SHOP = id("mask_shop");
    public static final ResourceLocation MASK_SALES = id("mask_sales");
    public static final ResourceLocation MASTER_SWORD_QUEST = id("master_sword");
    public static final ResourceLocation LIGHT_ARROWS = id("light_arrows");
    public static final ResourceLocation BIGGORON_SWORD_QUEST = id("biggoron_sword");
    public static final List<ResourceLocation> SKILL_ORDER = List.of(SWORD_BASIC, HELM_SPLITTER, DODGE, LEAPING_BLOW,
            PARRY, DASH, SPIN_ATTACK, SUPER_SPIN_ATTACK, SWORD_BEAM, SWORD_BREAK, MORTAL_DRAW, BONUS_HEART,
            RISING_CUT, ENDING_BLOW, FLASH_ASSAULT, DOUBLE_JUMP, CONTINUOUS_FLASH);
    public static final Set<ResourceLocation> SKILLS = Set.copyOf(SKILL_ORDER);
    public static final Set<ResourceLocation> SONGS = ids(
            "epona", "healing", "saria", "soaring", "storms", "sun", "time", "bolero", "minuet", "prelude",
            "oath", "nocturne", "requiem", "serenade", "lullaby", "scarecrow");
    public static final Set<ResourceLocation> QUESTS = ids(
            "zelda_talk", "pendants", "zelda_letter", "mask_shop", "mask_sales", "master_sword",
            "light_arrows", "biggoron_sword");

    private ZSSContentIds() {
    }

    private static Set<ResourceLocation> ids(String... paths) {
        return Arrays.stream(paths).map(ZSSContentIds::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }
}
