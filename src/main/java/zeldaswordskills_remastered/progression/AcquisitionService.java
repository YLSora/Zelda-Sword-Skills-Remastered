package zeldaswordskills_remastered.progression;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraftforge.registries.ForgeRegistries;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.entity.LegacyCreature;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.item.StageNineToolItem;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.List;
import java.util.Map;

/** Server-authoritative survival acquisition rules that are not naturally represented by loot tables. */
public final class AcquisitionService {
    public static final String LON_LON_COW = ZeldaSwordSkills_Remastered.MOD_ID + ":lon_lon_cow";
    public static final String LON_LON_COOLDOWN = ZeldaSwordSkills_Remastered.MOD_ID + ":lon_lon_milk_after";
    private static final String JELLY_UNLOCK_PREFIX = ZeldaSwordSkills_Remastered.MOD_ID + ":jelly_trade_";
    private static final int LON_LON_WINDOW = 6_000;
    private static final int LON_LON_COOLDOWN_TICKS = 24_000;
    private static final int TEMPERED_SWORD_KILLS = 300;
    public static final int GOLDEN_SWORD_REQUIRED_KILLS = TEMPERED_SWORD_KILLS + 1;

    private static final Map<String, PotionTrade> JELLY_TRADES = Map.of(
            "chu_jelly_red", new PotionTrade("red_potion", 8),
            "chu_jelly_green", new PotionTrade("green_potion", 16),
            "chu_jelly_blue", new PotionTrade("blue_potion", 24),
            "chu_jelly_yellow", new PotionTrade("yellow_potion", 32));

    private AcquisitionService() {
    }

    public static void initializePlayer(ServerPlayer player, ZSSPlayerData data) {
        if (data.receivedStartingGear()) return;
        data.markReceivedStartingGear();
        if (!ZSSConfig.SERVER.giveStartingItems.get()) return;
        ItemStack orb = new ItemStack(ZSSRegistries.getItem("skill_orb"));
        orb.getOrCreateTag().putString(ProgressionItem.SKILL_TAG, ZSSContentIds.SWORD_BASIC.toString());
        give(player, orb);
        give(player, new ItemStack(ZSSRegistries.getItem("links_house")));
    }

    public static boolean interact(ServerPlayer player, Entity target, InteractionHand hand) {
        if (!target.isAlive() || target.level() != player.level()) return false;
        ItemStack held = player.getItemInHand(hand);
        if (target instanceof LegacyCreature creature && creature.kind() == LegacyCreature.Kind.FAIRY
                && held.is(Items.GLASS_BOTTLE)) {
            consumeContainer(player, hand, held, stack("fairy_bottle"));
            ZSSAdvancementService.fairyProgress(player, true, false, false);
            creature.discard();
            return true;
        }
        if (target instanceof zeldaswordskills_remastered.entity.FairyCreature fairy
                && fairy.canUpgradeEquipment() && upgradeAtFairy(player, hand, held, fairy)) return true;
        if (target instanceof Cow cow && held.is(Items.GLASS_BOTTLE)) return milkLonLonCow(player, cow, hand, held);
        return target instanceof Villager villager && unlockJellyTrade(player, villager, held);
    }

    public static boolean repairGiantSword(ServerPlayer player, Entity target) {
        if (!(target instanceof zeldaswordskills_remastered.entity.npc.QuestNpc npc)
                || npc.role() != zeldaswordskills_remastered.entity.npc.QuestNpc.Role.GORON
                || !player.getMainHandItem().is(ZSSRegistries.BROKEN_SWORD.get())) return false;
        boolean worthy = zeldaswordskills_remastered.capability.ZSSCapabilities.get(player)
                .map(data -> data.skills().getOrDefault(ZSSContentIds.BONUS_HEART, 0) >= 10).orElse(false);
        if (!worthy) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.giant_sword_unworthy"));
            return true;
        }
        ZSSAdvancementService.swordProgress(player, "sword.broken");
        if (!consume(player, Items.EMERALD, 5)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.giant_sword_cost"));
            return true;
        }
        player.getMainHandItem().shrink(1);
        give(player, stack("giant_sword"));
        return true;
    }

    public static void recordTemperedSwordKill(ServerPlayer player, LivingEntity victim) {
        if (!(victim instanceof Monster)) return;
        ItemStack sword = player.getMainHandItem();
        if (!sword.is(ZSSRegistries.TEMPERED_SWORD.get())) return;
        int kills = sword.getOrCreateTag().getInt("zss_kills");
        sword.getOrCreateTag().putInt("zss_kills", Math.min(GOLDEN_SWORD_REQUIRED_KILLS, kills + 1));
        if (kills < GOLDEN_SWORD_REQUIRED_KILLS && kills + 1 >= GOLDEN_SWORD_REQUIRED_KILLS)
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "message.zeldaswordskills_remastered.tempered_sword_ready"));
        if (kills < TEMPERED_SWORD_KILLS && kills + 1 >= TEMPERED_SWORD_KILLS)
            ZSSAdvancementService.swordProgress(player, "sword.evil");
    }

    private static boolean upgradeAtFairy(ServerPlayer player, InteractionHand hand, ItemStack held,
                                         zeldaswordskills_remastered.entity.FairyCreature fairy) {
        ItemStack result;
        int emeralds = 0;
        if (player.isShiftKeyDown() && held.getItem() instanceof zeldaswordskills_remastered.item.StageNineToolItem tool
                && tool.kind() == zeldaswordskills_remastered.item.StageNineToolItem.Kind.SLINGSHOT) {
            int divisor = held.is(ZSSRegistries.getItem("slingshot")) ? 5 : held.is(ZSSRegistries.getItem("scattershot")) ? 7 : 10;
            int hearts = zeldaswordskills_remastered.capability.ZSSCapabilities.get(player)
                    .map(data -> data.skillLevel(ZSSContentIds.BONUS_HEART)).orElse(0);
            var power = net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS;
            int oldLevel = held.getEnchantmentLevel(power);
            int newLevel = Math.min(5, hearts / divisor);
            if (newLevel <= oldLevel) return false;
            emeralds = (newLevel - oldLevel) * divisor * 2;
            if (!consume(player, Items.EMERALD, emeralds)) return false;
            var enchantments = new java.util.LinkedHashMap<>(net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(held));
            enchantments.put(power, newLevel);
            net.minecraft.world.item.enchantment.EnchantmentHelper.setEnchantments(enchantments, held);
            ZSSAdvancementService.fairyProgress(player, false, true, true);
            fairy.discard();
            return true;
        } else if (held.getItem() instanceof zeldaswordskills_remastered.item.ZeldaCombatItems.HeroBow) {
            int bowLevel = zeldaswordskills_remastered.item.ZeldaCombatItems.HeroBow.upgradeLevel(held);
            if (bowLevel >= 3) return false;
            result = held.copy();
            result.getOrCreateTag().putInt("fairy_level", bowLevel + 1);
            emeralds = (bowLevel + 1) * 192;
        } else if (held.is(ZSSRegistries.getItem("slingshot"))) {
            result = stack("scattershot");
            emeralds = 128;
        } else if (held.is(ZSSRegistries.getItem("scattershot"))) {
            result = stack("supershot");
            emeralds = 320;
        } else if (held.is(ZSSRegistries.getItem("hookshot"))) {
            result = StageNineToolItem.upgradeHook(held, "claw_upgrade");
            emeralds = 128;
        } else if (held.is(ZSSRegistries.getItem("stoneshot"))) {
            result = StageNineToolItem.upgradeHook(held, "multi_hook_upgrade");
            emeralds = 320;
        } else if (held.is(ZSSRegistries.getItem("whip"))) {
            result = StageNineToolItem.transform(held, "magic_whip");
            emeralds = 320;
        } else if (held.is(ZSSRegistries.HYLIAN_SHIELD.get()) && hasItem(player, ZSSRegistries.TRUE_MASTER_SWORD.get())) {
            result = stack("mirror_shield");
        } else if (held.is(ZSSRegistries.getItem("boomerang")) && zeldaswordskills_remastered.capability.ZSSCapabilities.get(player)
                .map(data -> data.skills().getOrDefault(ZSSContentIds.BONUS_HEART, 0)
                        >= Math.max(1, zeldaswordskills_remastered.config.ZSSConfig.SERVER.maximumHeartContainers.get() / 2)).orElse(false)) {
            result = stack("magic_boomerang");
        } else if (held.is(ZSSRegistries.TEMPERED_SWORD.get())
                && held.getOrCreateTag().getInt("zss_kills") >= GOLDEN_SWORD_REQUIRED_KILLS) {
            result = stack("golden_sword");
        } else {
            return false;
        }
        if (emeralds > 0 && !consume(player, Items.EMERALD, emeralds)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.fairy_upgrade_cost", emeralds));
            return true;
        }
        if (emeralds > 0) ZSSAdvancementService.fairyProgress(player, false, true, false);
        if (held.hasCustomHoverName()) result.setHoverName(held.getHoverName());
        player.setItemInHand(hand, result);
        ZSSAdvancementService.fairyUpgrade(player, result);
        if (result.getItem() instanceof zeldaswordskills_remastered.item.ZeldaCombatItems.HeroBow)
            ZSSAdvancementService.bowUpgraded(player, zeldaswordskills_remastered.item.ZeldaCombatItems.HeroBow.upgradeLevel(result));
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zeldaswordskills_remastered.fairy_upgrade_complete", result.getHoverName()));
        fairy.discard();
        return true;
    }

    private static boolean milkLonLonCow(ServerPlayer player, Cow cow, InteractionHand hand, ItemStack held) {
        if (cow.isBaby()) return false;
        long now = player.level().getGameTime();
        long markedUntil = cow.getPersistentData().getLong(LON_LON_COW);
        long milkAfter = cow.getPersistentData().getLong(LON_LON_COOLDOWN);
        if (markedUntil < now || milkAfter > now) return false;
        consumeContainer(player, hand, held, new ItemStack(ZSSRegistries.getItem("lon_lon_milk")));
        ZSSAdvancementService.specialProgress(player);
        cow.getPersistentData().remove(LON_LON_COW);
        cow.getPersistentData().putLong(LON_LON_COOLDOWN, now + LON_LON_COOLDOWN_TICKS);
        return true;
    }

    private static boolean unlockJellyTrade(ServerPlayer player, Villager villager, ItemStack held) {
        if (villager.isBaby() || villager.getVillagerData().getProfession() != VillagerProfession.CLERIC) return false;
        ResourceLocation heldId = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (heldId == null || !heldId.getNamespace().equals(ZeldaSwordSkills_Remastered.MOD_ID)) return false;
        PotionTrade trade = JELLY_TRADES.get(heldId.getPath());
        if (trade == null) return false;
        String unlock = JELLY_UNLOCK_PREFIX + heldId.getPath();
        if (!villager.getPersistentData().getBoolean(unlock)) {
            held.shrink(1);
            villager.getPersistentData().putBoolean(unlock, true);
            ZSSAdvancementService.specialProgress(player);
        }
        Item jelly = ZSSRegistries.getItem(heldId.getPath());
        Item potion = ZSSRegistries.getItem(trade.output());
        boolean exists = villager.getOffers().stream().anyMatch(offer -> offer.getBaseCostA().is(jelly)
                && offer.getResult().is(potion));
        if (!exists) villager.getOffers().add(new MerchantOffer(new ItemStack(jelly, 4),
                new ItemStack(Items.EMERALD, trade.emeralds()), new ItemStack(potion), 12, 2, 0.05F));
        villager.openTradingScreen(player, villager.getDisplayName(), villager.getVillagerData().getLevel());
        return true;
    }

    public static void markLonLonCows(ServerPlayer player) {
        long until = player.level().getGameTime() + LON_LON_WINDOW;
        var cows = player.level().getEntitiesOfClass(Cow.class, player.getBoundingBox().inflate(8.0D, 4.0D, 8.0D),
                cow -> !cow.isBaby() && cow.getPersistentData().getLong(LON_LON_COOLDOWN) <= player.level().getGameTime())
                ;
        cows.forEach(cow -> cow.getPersistentData().putLong(LON_LON_COW, until));
        if (!cows.isEmpty()) ZSSAdvancementService.specialProgress(player);
    }

    public static List<ItemStack> rollMobDrops(LivingEntity victim, RandomSource random) {
        if (!(victim instanceof Monster) || victim instanceof LegacyCreature creature
                && (creature.kind().family() == LegacyCreature.Family.FAIRY
                || creature.kind() == LegacyCreature.Kind.DARKNUT_BOSS)) return List.of();
        return random.nextFloat() < 0.05F ? List.of(stack("small_heart")) : List.of();
    }

    private static boolean hasItem(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return true;
        }
        return false;
    }

    private static boolean consume(ServerPlayer player, Item item, int amount) {
        if (player.getAbilities().instabuild) return true;
        int found = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(item)) found += stack.getCount();
        }
        if (found < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int removed = Math.min(remaining, stack.getCount());
            stack.shrink(removed);
            remaining -= removed;
        }
        return true;
    }

    private static void consumeContainer(ServerPlayer player, InteractionHand hand, ItemStack held, ItemStack result) {
        if (player.getAbilities().instabuild) {
            give(player, result);
        } else if (held.getCount() == 1) {
            player.setItemInHand(hand, result);
        } else {
            held.shrink(1);
            give(player, result);
        }
    }

    private static ItemStack stack(String path) {
        return new ItemStack(ZSSRegistries.getItem(path));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private record PotionTrade(String output, int emeralds) {
    }
}
