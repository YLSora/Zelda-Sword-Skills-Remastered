package zeldaswordskills_remastered.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.entity.npc.QuestNpc;
import zeldaswordskills_remastered.item.InstrumentItem;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.SongDefinition;

import java.util.List;
import java.util.Map;

public final class QuestService {
    private static final int ZELDA_OCARINA_TICKS = 45;
    private static final int BIGGORON_WAIT_TICKS = 48_000;
    private static final String NEXT_SKULLTULA_REWARD = "zss_next_skulltula_reward";

    private static final List<MaskTrade> MASKS = List.of(
            new MaskTrade("keaton_mask", 8, 16),
            new MaskTrade("skull_mask", 20, 10),
            new MaskTrade("spooky_mask", 16, 8),
            new MaskTrade("mask_of_scents", 32, 32),
            new MaskTrade("couples_mask", 40, 32),
            new MaskTrade("bunny_hood", 1, 64));

    private static final List<BiggoronTrade> BIGGORON_TRADES = List.of(
            new BiggoronTrade("tentacle", "pocket_egg", "Talon", VillagerProfession.FARMER, true, false),
            new BiggoronTrade("pocket_egg", "cojiro", "Cucco Lady", VillagerProfession.FARMER, false, false),
            new BiggoronTrade("cojiro", "odd_mushroom", "Grog", VillagerProfession.BUTCHER, false, false),
            new BiggoronTrade("odd_mushroom", "odd_potion", "Old Hag", VillagerProfession.LIBRARIAN, false, false),
            new BiggoronTrade("odd_potion", "poacher_saw", "Grog", VillagerProfession.BUTCHER, false, false),
            new BiggoronTrade("poacher_saw", "goron_sword", "Mutoh", VillagerProfession.TOOLSMITH, false, false),
            new BiggoronTrade("goron_sword", "prescription", "Biggoron", null, false, true),
            new BiggoronTrade("prescription", "eyeball_frog", "King Zora", VillagerProfession.CLERIC, false, false),
            new BiggoronTrade("eyeball_frog", "eye_drops", "Lake Scientist", VillagerProfession.LIBRARIAN, false, false),
            new BiggoronTrade("eye_drops", "claim_check", "Biggoron", null, false, true),
            new BiggoronTrade("claim_check", "biggoron_sword", "Biggoron", null, false, true));

    private QuestService() {
    }

    public static void initialize(ServerPlayer player, ZSSPlayerData data) {
        if (!data.quests().containsKey(ZSSContentIds.BIGGORON_SWORD_QUEST)) {
            set(data, ZSSContentIds.BIGGORON_SWORD_QUEST, QuestStates.STARTED, 0, player.level().getGameTime());
            sync(player);
        }
    }

    /** Administrative completion grants outstanding rewards once per quest completion. */
    public static boolean achieve(ServerPlayer player, ResourceLocation quest) {
        if (!ZSSContentIds.QUESTS.contains(quest)) return false;
        ZSSPlayerData data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return false;
        ZSSPlayerData.QuestProgress previous = progress(data, quest);
        settlePendingItems(player, data, quest);
        long now = player.level().getGameTime();
        int step = quest.equals(ZSSContentIds.PENDANTS) ? 3
                : quest.equals(ZSSContentIds.MASTER_SWORD_QUEST) ? 2
                : quest.equals(ZSSContentIds.MASK_SALES) ? MASKS.size()
                : quest.equals(ZSSContentIds.BIGGORON_SWORD_QUEST) ? BIGGORON_TRADES.size() : 1;
        set(data, quest, QuestStates.COMPLETE, step, now);
        if (!previous.state().equals(QuestStates.COMPLETE)) {
            if (quest.equals(ZSSContentIds.MASTER_SWORD_QUEST) && previous.step() == 0) returnPendants(player);
            giveCompletionReward(player, quest);
        }
        if (quest.equals(ZSSContentIds.ZELDA_TALK)) start(data, ZSSContentIds.PENDANTS, now);
        if (quest.equals(ZSSContentIds.PENDANTS)) {
            start(data, ZSSContentIds.ZELDA_LETTER, now);
            start(data, ZSSContentIds.MASTER_SWORD_QUEST, now);
        }
        if (quest.equals(ZSSContentIds.MASTER_SWORD_QUEST)) start(data, ZSSContentIds.LIGHT_ARROWS, now);
        if (quest.equals(ZSSContentIds.MASK_SHOP)) start(data, ZSSContentIds.MASK_SALES, now);
        ZSSAdvancementService.questCompleted(player, quest);
        sync(player);
        return true;
    }

    public static boolean reset(ServerPlayer player, ResourceLocation quest) {
        if (!ZSSContentIds.QUESTS.contains(quest)) return false;
        ZSSPlayerData data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return false;
        resetProgress(player, data, quest);
        boolean unlocked = quest.equals(ZSSContentIds.PENDANTS) && complete(data, ZSSContentIds.ZELDA_TALK)
                || quest.equals(ZSSContentIds.ZELDA_LETTER) && progress(data, ZSSContentIds.PENDANTS).step() > 0
                || quest.equals(ZSSContentIds.MASTER_SWORD_QUEST) && complete(data, ZSSContentIds.PENDANTS)
                || quest.equals(ZSSContentIds.LIGHT_ARROWS) && complete(data, ZSSContentIds.MASTER_SWORD_QUEST)
                || quest.equals(ZSSContentIds.MASK_SALES) && complete(data, ZSSContentIds.MASK_SHOP);
        if (unlocked) start(data, quest, player.level().getGameTime());
        sync(player);
        return true;
    }

    private static boolean complete(ZSSPlayerData data, ResourceLocation quest) {
        return progress(data, quest).state().equals(QuestStates.COMPLETE);
    }

    public static boolean resetAll(ServerPlayer player) {
        ZSSPlayerData data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return false;
        for (ResourceLocation quest : ZSSContentIds.QUESTS) resetProgress(player, data, quest);
        sync(player);
        return true;
    }

    private static void resetProgress(ServerPlayer player, ZSSPlayerData data, ResourceLocation quest) {
        settlePendingItems(player, data, quest);
        set(data, quest, quest.equals(ZSSContentIds.BIGGORON_SWORD_QUEST)
                ? QuestStates.STARTED : QuestStates.NOT_STARTED, 0, player.level().getGameTime());
    }

    private static void settlePendingItems(ServerPlayer player, ZSSPlayerData data, ResourceLocation quest) {
        if (quest.equals(ZSSContentIds.ZELDA_TALK)) returnEscrow(player, data);
        if (quest.equals(ZSSContentIds.MASK_SALES)) data.setBorrowedMask(null);
    }

    public static void tick(ServerPlayer player, ZSSPlayerData data) {
        ZSSPlayerData.QuestProgress talk = progress(data, ZSSContentIds.ZELDA_TALK);
        if (talk.state().equals(QuestStates.OCARINA_HELD)
                && player.level().getGameTime() >= talk.updatedAt() + ZELDA_OCARINA_TICKS) {
            returnEscrow(player, data);
            set(data, ZSSContentIds.ZELDA_TALK, QuestStates.COMPLETE, 1, player.level().getGameTime());
            ZSSAdvancementService.questCompleted(player, ZSSContentIds.ZELDA_TALK);
            start(data, ZSSContentIds.PENDANTS, player.level().getGameTime());
            tell(player, "zelda_talk.complete");
            sync(player);
        }
    }

    public static void returnEscrowOnLogout(ServerPlayer player) {
        ZSSCapabilities.get(player).ifPresent(data -> {
            if (!data.escrowOcarina().isEmpty()) {
                returnEscrow(player, data);
                ZSSPlayerData.QuestProgress talk = progress(data, ZSSContentIds.ZELDA_TALK);
                if (talk.state().equals(QuestStates.OCARINA_HELD)) {
                    set(data, ZSSContentIds.ZELDA_TALK, QuestStates.STARTED, 0, player.level().getGameTime());
                }
                sync(player);
            }
        });
    }

    public static void interactNpc(ServerPlayer player, QuestNpc npc, InteractionHand hand) {
        ZSSCapabilities.get(player).ifPresent(data -> {
            initialize(player, data);
            switch (npc.role()) {
                case ZELDA -> interactZelda(player, data, hand);
                case MASK_TRADER -> interactMaskTrader(player, data, npc, hand);
                case GORON -> hintBiggoron(player, data, npc);
                case BARNES -> interactBarnes(player, npc, hand);
                case ORCA -> interactOrca(player, data, hand);
            }
        });
    }

    public static boolean interactVillager(ServerPlayer player, Villager villager, InteractionHand hand) {
        final boolean[] handled = {false};
        ZSSCapabilities.get(player).ifPresent(data -> {
            initialize(player, data);
            if (tryConvertZelda(player, data, villager, hand)
                    || tryConvertBarnes(player, villager, hand)
                    || tryConvertMaskShop(player, data, villager, hand)
                    || isCursedMan(villager) && !player.getItemInHand(hand).is(Items.NAME_TAG)
                    && interactCursedMan(player, data, villager, hand)
                    || trySellMask(player, data, villager, hand)) {
                handled[0] = true;
            } else if (isBiggoronTarget(villager)) {
                tell(player, "biggoron.hint");
                handled[0] = true;
            }
        });
        return handled[0];
    }

    public static boolean attackForTrade(ServerPlayer player, Entity target) {
        final boolean[] handled = {false};
        ZSSCapabilities.get(player).ifPresent(data -> {
            initialize(player, data);
            if (target instanceof Villager villager && isCursedMan(villager)
                    && isItem(player.getMainHandItem(), "skulltula_token")) {
                interactCursedMan(player, data, villager, InteractionHand.MAIN_HAND);
                handled[0] = true;
                return;
            }
            if (target instanceof Villager villager && tryConvertOrca(player, data, villager)) {
                handled[0] = true;
                return;
            }
            int matched = matchingBiggoronTrade(player.getMainHandItem(), target);
            if (matched < 0) return;
            ZSSPlayerData.QuestProgress quest = progress(data, ZSSContentIds.BIGGORON_SWORD_QUEST);
            if (matched > quest.step() || matched == 10 && quest.state().equals(QuestStates.COMPLETE)) {
                tell(player, "biggoron.wrong_stage");
                handled[0] = true;
                return;
            }
            if (matched == 10 && (quest.step() != 10 || !quest.state().equals(QuestStates.CLAIM_WAIT))) {
                handled[0] = true;
                return;
            }
            if (matched == 10 && player.level().getGameTime() < quest.updatedAt()) {
                tell(player, "biggoron.not_ready");
                handled[0] = true;
                return;
            }
            BiggoronTrade trade = BIGGORON_TRADES.get(matched);
            ItemStack held = player.getMainHandItem();
            held.shrink(1);
            if (matched == BIGGORON_TRADES.size() - 1) giveCompletionReward(player, ZSSContentIds.BIGGORON_SWORD_QUEST);
            else give(player, stack(trade.output(), 1));
            ZSSAdvancementService.treasureTraded(player, trade.input());
            if (matched == quest.step()) {
                if (matched == 10) {
                    set(data, ZSSContentIds.BIGGORON_SWORD_QUEST, QuestStates.COMPLETE, 11, player.level().getGameTime());
                    ZSSAdvancementService.questCompleted(player, ZSSContentIds.BIGGORON_SWORD_QUEST);
                    tell(player, "biggoron.complete");
                } else if (matched == 9) {
                    set(data, ZSSContentIds.BIGGORON_SWORD_QUEST, QuestStates.CLAIM_WAIT, 10,
                            player.level().getGameTime() + BIGGORON_WAIT_TICKS);
                    tell(player, "biggoron.wait");
                } else {
                    set(data, ZSSContentIds.BIGGORON_SWORD_QUEST, QuestStates.STARTED, matched + 1, player.level().getGameTime());
                    tell(player, "biggoron.trade");
                }
                sync(player);
            }
            handled[0] = true;
        });
        return handled[0];
    }

    private static void interactZelda(ServerPlayer player, ZSSPlayerData data, InteractionHand hand) {
        long now = player.level().getGameTime();
        ItemStack held = player.getItemInHand(hand);
        ZSSPlayerData.QuestProgress talk = progress(data, ZSSContentIds.ZELDA_TALK);
        if (!talk.state().equals(QuestStates.COMPLETE)) {
            if (talk.state().equals(QuestStates.OCARINA_HELD)) {
                tell(player, "zelda_talk.performing");
            } else if (held.is(ZSSRegistries.FAIRY_OCARINA.get())) {
                data.setEscrowOcarina(held);
                held.shrink(1);
                set(data, ZSSContentIds.ZELDA_TALK, QuestStates.OCARINA_HELD, 0, now);
                tell(player, "zelda_talk.accepted");
                sync(player);
            } else {
                set(data, ZSSContentIds.ZELDA_TALK, QuestStates.STARTED, 0, now);
                tell(player, "zelda_talk.start");
                sync(player);
            }
            return;
        }

        ZSSPlayerData.QuestProgress letter = progress(data, ZSSContentIds.ZELDA_LETTER);
        ZSSPlayerData.QuestProgress shop = progress(data, ZSSContentIds.MASK_SHOP);
        if (!letter.state().equals(QuestStates.COMPLETE) && !letter.state().equals(QuestStates.NOT_STARTED)
                && !shop.state().equals(QuestStates.NOT_STARTED)) {
            if (shop.state().equals(QuestStates.COMPLETE)) {
                set(data, ZSSContentIds.ZELDA_LETTER, QuestStates.COMPLETE, 1, now);
            } else {
                giveCompletionReward(player, ZSSContentIds.ZELDA_LETTER);
                set(data, ZSSContentIds.ZELDA_LETTER, QuestStates.COMPLETE, 1, now);
                tell(player, "zelda_letter.complete");
            }
            sync(player);
            return;
        }

        if (deliverPendant(player, data, held, now)) return;

        ZSSPlayerData.QuestProgress master = progress(data, ZSSContentIds.MASTER_SWORD_QUEST);
        if (master.state().equals(QuestStates.STARTED) && master.step() == 0) {
            returnPendants(player);
            set(data, ZSSContentIds.MASTER_SWORD_QUEST, QuestStates.STARTED, 1, now);
            tell(player, "master_sword.pendants_returned");
            sync(player);
            return;
        }
        if (master.state().equals(QuestStates.STARTED) && master.step() == 1 && held.is(ZSSRegistries.MASTER_SWORD.get())) {
            giveCompletionReward(player, ZSSContentIds.MASTER_SWORD_QUEST);
            set(data, ZSSContentIds.MASTER_SWORD_QUEST, QuestStates.COMPLETE, 2, now);
            ZSSAdvancementService.questCompleted(player, ZSSContentIds.MASTER_SWORD_QUEST);
            start(data, ZSSContentIds.LIGHT_ARROWS, now);
            tell(player, "master_sword.complete");
            sync(player);
            return;
        }

        ZSSPlayerData.QuestProgress arrows = progress(data, ZSSContentIds.LIGHT_ARROWS);
        if (arrows.state().equals(QuestStates.STARTED) && isItem(held, "hero_bow")) {
            giveCompletionReward(player, ZSSContentIds.LIGHT_ARROWS);
            set(data, ZSSContentIds.LIGHT_ARROWS, QuestStates.COMPLETE, 1, now);
            ZSSAdvancementService.questCompleted(player, ZSSContentIds.LIGHT_ARROWS);
            tell(player, "light_arrows.complete");
            sync(player);
            return;
        }

        if (master.state().equals(QuestStates.COMPLETE) && isStrongInstrument(held)) {
            if (data.learnSong(ZSSContentIds.TIME)) {
                ZSSAdvancementService.songLearned(player, ZSSContentIds.TIME, data.songs().size());
                tell(player, "song.time.learned");
                sync(player);
            } else tell(player, "song.time.known");
            return;
        }
        tell(player, "zelda.story");
        if (arrows.state().equals(QuestStates.COMPLETE)) {
            MerchantOffers offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 8), stack("light_arrow", 1), 99, 1, 0.0F));
            QuestNpc npc = nearestNpc(player, QuestNpc.Role.ZELDA);
            if (npc != null) npc.openOffers(player, offers);
        }
    }

    private static boolean deliverPendant(ServerPlayer player, ZSSPlayerData data, ItemStack held, long now) {
        ZSSPlayerData.QuestProgress quest = progress(data, ZSSContentIds.PENDANTS);
        if (!quest.state().equals(QuestStates.STARTED)) return false;
        Item expected = switch (quest.step()) {
            case 0 -> ZSSRegistries.PENDANT_WISDOM.get();
            case 1 -> ZSSRegistries.PENDANT_COURAGE.get();
            case 2 -> ZSSRegistries.PENDANT_POWER.get();
            default -> null;
        };
        if (expected == null || !held.is(expected)) {
            if (held.is(ZSSRegistries.PENDANT_WISDOM.get()) || held.is(ZSSRegistries.PENDANT_COURAGE.get())
                    || held.is(ZSSRegistries.PENDANT_POWER.get())) {
                tell(player, "pendants.wrong_order");
                return true;
            }
            return false;
        }
        held.shrink(1);
        int next = quest.step() + 1;
        if (next == 1) start(data, ZSSContentIds.ZELDA_LETTER, now);
        if (next == 3) {
            giveCompletionReward(player, ZSSContentIds.PENDANTS);
            set(data, ZSSContentIds.PENDANTS, QuestStates.COMPLETE, 3, now);
            ZSSAdvancementService.questCompleted(player, ZSSContentIds.PENDANTS);
            start(data, ZSSContentIds.MASTER_SWORD_QUEST, now);
            tell(player, "pendants.complete");
        } else {
            set(data, ZSSContentIds.PENDANTS, QuestStates.STARTED, next, now);
            tell(player, "pendants.delivered");
        }
        sync(player);
        return true;
    }

    private static void interactMaskTrader(ServerPlayer player, ZSSPlayerData data, QuestNpc trader, InteractionHand hand) {
        long now = player.level().getGameTime();
        ZSSPlayerData.QuestProgress shop = progress(data, ZSSContentIds.MASK_SHOP);
        if (shop.state().equals(QuestStates.NOT_STARTED)) {
            start(data, ZSSContentIds.MASK_SHOP, now);
            tell(player, "mask_shop.start");
            sync(player);
            return;
        }
        if (!shop.state().equals(QuestStates.COMPLETE)) {
            tell(player, "mask_shop.letter");
            return;
        }

        ZSSPlayerData.QuestProgress sales = progress(data, ZSSContentIds.MASK_SALES);
        if (sales.state().equals(QuestStates.NOT_STARTED)) {
            start(data, ZSSContentIds.MASK_SALES, now);
            sales = progress(data, ZSSContentIds.MASK_SALES);
        }
        if (sales.state().equals(QuestStates.COMPLETE)) {
            if (isStrongInstrument(player.getItemInHand(hand))) {
                if (data.learnSong(ZSSContentIds.HEALING)) { ZSSAdvancementService.songLearned(player, ZSSContentIds.HEALING, data.songs().size()); tell(player, "song.healing.learned"); }
                else tell(player, "song.healing.known");
                sync(player);
            } else {
                tell(player, "mask_sales.complete_story");
                MerchantOffers offers = new MerchantOffers();
                addMaskOffer(offers, "keaton_mask", 16);
                addMaskOffer(offers, "skull_mask", 20);
                addMaskOffer(offers, "spooky_mask", 16);
                addMaskOffer(offers, "mask_of_scents", 32);
                addMaskOffer(offers, "couples_mask", 40);
                addMaskOffer(offers, "bunny_hood", 16);
                addMaskOffer(offers, "deku_mask", 24);
                addMaskOffer(offers, "goron_mask", 32);
                addMaskOffer(offers, "gerudo_mask", 32);
                addMaskOffer(offers, "zora_mask", 32);
                addMaskOffer(offers, "fierce_deity_mask", 64);
                trader.openOffers(player, offers);
            }
            return;
        }
        int index = sales.step();
        if (index < 0 || index >= MASKS.size()) return;
        MaskTrade mask = MASKS.get(index);
        if (sales.state().equals(QuestStates.STARTED) || sales.state().equals(QuestStates.PAID)) {
            give(player, stack(mask.item(), 1));
            data.setBorrowedMask(id(mask.item()));
            set(data, ZSSContentIds.MASK_SALES, QuestStates.BORROWED, index, now);
            tell(player, "mask_sales.borrowed");
            sync(player);
            return;
        }
        if (sales.state().equals(QuestStates.SOLD)) {
            if (!consume(player, Items.EMERALD, mask.buyPrice())) {
                tell(player, "mask_sales.payment_needed", mask.buyPrice());
                return;
            }
            data.setBorrowedMask(null);
            int next = index + 1;
            if (next == MASKS.size()) {
                giveCompletionReward(player, ZSSContentIds.MASK_SALES);
                set(data, ZSSContentIds.MASK_SALES, QuestStates.COMPLETE, next, now);
                ZSSAdvancementService.questCompleted(player, ZSSContentIds.MASK_SALES);
                tell(player, "mask_sales.complete");
            } else {
                set(data, ZSSContentIds.MASK_SALES, QuestStates.PAID, next, now);
                tell(player, "mask_sales.paid");
            }
            sync(player);
            return;
        }
        tell(player, "mask_sales.sell_first");
    }

    private static boolean tryConvertMaskShop(ServerPlayer player, ZSSPlayerData data, Villager villager, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isItem(held, "zeldas_letter") || villager.isBaby()) return false;
        ZSSPlayerData.QuestProgress shop = progress(data, ZSSContentIds.MASK_SHOP);
        if (shop.state().equals(QuestStates.NOT_STARTED)) return false;
        QuestNpc trader = ZSSRegistries.MASK_TRADER.get().create(player.serverLevel());
        if (trader == null) return false;
        held.shrink(1);
        trader.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        if (villager.hasCustomName()) trader.setCustomName(villager.getCustomName());
        player.serverLevel().addFreshEntity(trader);
        villager.discard();
        if (!shop.state().equals(QuestStates.COMPLETE)) {
            set(data, ZSSContentIds.MASK_SHOP, QuestStates.COMPLETE, 1, player.level().getGameTime());
            ZSSAdvancementService.questCompleted(player, ZSSContentIds.MASK_SHOP);
            ZSSAdvancementService.maskProgress(player, true, false, false);
            start(data, ZSSContentIds.MASK_SALES, player.level().getGameTime());
        }
        ZSSPlayerData.QuestProgress letter = progress(data, ZSSContentIds.ZELDA_LETTER);
        if (!letter.state().equals(QuestStates.COMPLETE)) {
            set(data, ZSSContentIds.ZELDA_LETTER, QuestStates.COMPLETE, 1, player.level().getGameTime());
        }
        tell(player, "mask_shop.complete");
        sync(player);
        return true;
    }

    private static boolean trySellMask(ServerPlayer player, ZSSPlayerData data, Villager villager, InteractionHand hand) {
        ZSSPlayerData.QuestProgress sales = progress(data, ZSSContentIds.MASK_SALES);
        if (!sales.state().equals(QuestStates.BORROWED) || sales.step() >= MASKS.size()) return false;
        MaskTrade mask = MASKS.get(sales.step());
        ItemStack held = player.getItemInHand(hand);
        if (!isItem(held, mask.item())) return false;
        held.shrink(1);
        give(player, new ItemStack(Items.EMERALD, mask.salePrice()));
        ZSSAdvancementService.maskProgress(player, false, true, false);
        set(data, ZSSContentIds.MASK_SALES, QuestStates.SOLD, sales.step(), player.level().getGameTime());
        tell(player, "mask_sales.sold");
        sync(player);
        return true;
    }

    private static void hintBiggoron(ServerPlayer player, ZSSPlayerData data, QuestNpc npc) {
        if (npc.role() != QuestNpc.Role.GORON) return;
        ZSSPlayerData.QuestProgress quest = progress(data, ZSSContentIds.BIGGORON_SWORD_QUEST);
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 20), stack("small_magic_jar", 1), 99, 1, 0.0F));
        offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 40), stack("large_magic_jar", 1), 99, 1, 0.0F));
        if (quest.state().equals(QuestStates.COMPLETE)) {
            tell(player, "biggoron.complete_story");
            offers.add(new MerchantOffer(stack("master_ore", 3), new ItemStack(Items.DIAMOND, 4),
                    new ItemStack(ZSSRegistries.BIGGORON_SWORD.get()), 99, 1, 0.0F));
        }
        else if (quest.state().equals(QuestStates.CLAIM_WAIT)) tell(player, "biggoron.wait");
        else tell(player, "biggoron.hint");
        npc.openOffers(player, offers);
    }

    public static boolean onSongPlayed(ServerPlayer player, QuestNpc npc, SongDefinition song, int strength) {
        if (npc.role() != QuestNpc.Role.GORON || !npc.getName().getString().equals("Darunia")
                || !song.id().equals(ZSSContentIds.SARIA)) return false;
        ZSSCapabilities.get(player).ifPresent(data -> {
            if (data.healedNpcs().contains(npc.getUUID())) {
                tell(player, "darunia.thanks");
            } else if (strength < 5) {
                tell(player, "darunia.weak");
            } else if (data.markNpcHealed(npc.getUUID())) {
                give(player, stack("silver_gauntlets", 1));
                tell(player, "darunia.complete");
                sync(player);
            }
        });
        return true;
    }

    private static void interactBarnes(ServerPlayer player, QuestNpc barnes, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.is(Items.COD)) {
            if (barnes.unlockWaterBomb()) {
                held.shrink(1);
                tell(player, "barnes.water_unlocked");
            } else tell(player, "barnes.water_known");
            return;
        }
        if (held.is(Items.MAGMA_CREAM)) {
            if (barnes.unlockFireBomb()) {
                held.shrink(1);
                tell(player, "barnes.fire_unlocked");
            } else tell(player, "barnes.fire_known");
            return;
        }
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 8), stack("standard_bomb", 1), 99, 1, 0.0F));
        if (barnes.waterBombUnlocked()) {
            offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 12), stack("water_bomb", 1), 99, 1, 0.0F));
        }
        if (barnes.fireBombUnlocked()) {
            offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, 16), stack("fire_bomb", 1), 99, 1, 0.0F));
        }
        offers.add(new MerchantOffer(stack("bomb_flower_seed", 1), new ItemStack(Items.EMERALD, 4), 99, 1, 0.0F));
        tell(player, "barnes.story");
        barnes.openOffers(player, offers);
    }

    private static void interactOrca(ServerPlayer player, ZSSPlayerData data, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isItem(held, "knights_crest")) {
            tell(player, data.knightsCrestsGiven() >= 100 ? "orca.master" : "orca.story");
            return;
        }
        int crests = data.knightsCrestsGiven();
        int spin = data.skills().getOrDefault(ZSSContentIds.SPIN_ATTACK, 0);
        int superSpin = data.skills().getOrDefault(ZSSContentIds.SUPER_SPIN_ATTACK, 0);
        int continuous = data.skills().getOrDefault(ZSSContentIds.CONTINUOUS_FLASH, 0);
        int pendingTier = data.orcaChoiceTier();
        if (pendingTier > 0) {
            tell(player, "orca.choose_pending");
            return;
        }
        int required = Math.min(Math.max(crests / 10 - 1, 0), 5);
        if (spin < 1 || superSpin + continuous < required) {
            tell(player, "orca.unfit");
            return;
        }
        if (!data.giveKnightsCrest()) {
            tell(player, "orca.master");
            return;
        }
        held.shrink(1);
        int total = data.knightsCrestsGiven();
        ZSSAdvancementService.orcaProgress(player, total, false, false);
        if (total % 10 == 0) {
            data.setOrcaChoiceTier(Math.min(total / 10, 5));
            askOrcaSkill(player);
        } else if (total == 1) tell(player, "orca.begin");
        else tell(player, "orca.redeem", total);
        sync(player);
    }

    private static boolean interactCursedMan(ServerPlayer player, ZSSPlayerData data, Villager villager, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!isItem(held, "skulltula_token") && data.skulltulaTokens() < 100) {
            if (data.skulltulaTokens() > 0) tell(player, "cursed_man.amount", data.skulltulaTokens());
            else tell(player, "cursed_man.story");
            return true;
        }
        if (data.skulltulaTokens() >= 100) {
            int days = ZSSConfig.SERVER.skulltulaRewardRate.get();
            long now = player.level().getGameTime();
            var persistent = villager.getPersistentData();
            // A newly named villager must also wait before offering recurring rewards.
            if (days > 0 && !persistent.contains(NEXT_SKULLTULA_REWARD)) {
                persistent.putLong(NEXT_SKULLTULA_REWARD, now + 24_000L * days);
            }
            if (days > 0 && now >= persistent.getLong(NEXT_SKULLTULA_REWARD)) {
                persistent.putLong(NEXT_SKULLTULA_REWARD, now + 24_000L * days);
                give(player, new ItemStack(Items.EMERALD, 64));
                tell(player, "cursed_man.reward", 100);
            } else tell(player, "cursed_man.complete");
            return true;
        }
        held.shrink(1);
        data.addSkulltulaToken();
        int total = data.skulltulaTokens();
        ItemStack reward = switch (total) {
            case 10 -> stack("whip", 1);
            case 20 -> stack("zora_tunic_chestplate", 1);
            case 30 -> cursedBombBag();
            case 40 -> zeldaswordskills_remastered.item.BigKeyItem.forDungeon(ZSSRegistries.getItem("big_key"),
                    zeldaswordskills_remastered.worldgen.DungeonType.values()[player.getRandom().nextInt(zeldaswordskills_remastered.worldgen.DungeonType.values().length)].id());
            case 50 -> randomSkillOrb(player, data);
            case 100 -> new ItemStack(Items.EMERALD, 64);
            default -> ItemStack.EMPTY;
        };
        if (total == 100 && ZSSConfig.SERVER.skulltulaRewardRate.get() > 0) {
            villager.getPersistentData().putLong(NEXT_SKULLTULA_REWARD,
                    player.level().getGameTime() + 24_000L * ZSSConfig.SERVER.skulltulaRewardRate.get());
        }
        if (!reward.isEmpty()) give(player, reward);
        tell(player, reward.isEmpty() ? "cursed_man.amount" : "cursed_man.reward", total);
        sync(player);
        return true;
    }

    private static ItemStack cursedBombBag() {
        ItemStack bag = stack("bomb_bag", 1);
        net.minecraft.nbt.CompoundTag bombs = bag.getOrCreateTagElement("bombs");
        bombs.putInt("standard_bomb", 10);
        return bag;
    }

    private static ItemStack randomSkillOrb(ServerPlayer player, ZSSPlayerData data) {
        List<ResourceLocation> candidates = ZSSContentIds.SKILLS.stream()
                .filter(id -> !id.equals(ZSSContentIds.BONUS_HEART)
                        && data.skillLevel(id) < data.skillMaximum(id)).toList();
        if (candidates.isEmpty()) return stack("light_arrow", 16);
        ItemStack orb = stack("skill_orb", 1);
        orb.getOrCreateTag().putString(zeldaswordskills_remastered.item.ProgressionItem.SKILL_TAG,
                candidates.get(player.getRandom().nextInt(candidates.size())).toString());
        return orb;
    }

    /** Applies the skill selected by the clickable Orca chat prompt. */
    public static boolean chooseOrcaSkill(ServerPlayer player, ResourceLocation skill) {
        ZSSPlayerData data = ZSSCapabilities.get(player).orElse(null);
        int tier = data == null ? 0 : data.orcaChoiceTier();
        if (tier <= 0 || (!skill.equals(ZSSContentIds.SUPER_SPIN_ATTACK)
                && !skill.equals(ZSSContentIds.CONTINUOUS_FLASH))) return false;
        if (data.knightsCrestsGiven() < tier * 10) return false;
        int current = data.skillLevel(skill);
        if (current >= data.skillMaximum(skill)) {
            tell(player, "orca.skill_maxed");
            return false;
        }
        data.setSkillLevel(skill, current + 1);
        data.setOrcaChoiceTier(0);
        ZSSAdvancementService.skillLearned(player, skill, data.skillLevel(skill), data.allSkillTypesLearned());
        tell(player, skill.equals(ZSSContentIds.SUPER_SPIN_ATTACK) ? "orca.super_spin" : "orca.continuous_flash",
                data.knightsCrestsGiven());
        sync(player);
        return true;
    }


    private static void askOrcaSkill(ServerPlayer player) {
        MutableComponent prompt = Component.literal("你要学习哪个技能？\n");
        prompt = prompt.append(Component.literal("【超级回旋斩】").withStyle(Style.EMPTY
                .withColor(0xFF5555).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/zss orca choose super_spin_attack"))));
        prompt = prompt.append(Component.literal("     "));
        prompt = prompt.append(Component.literal("【连续冲刺斩】").withStyle(Style.EMPTY
                .withColor(0x55FF55).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/zss orca choose continuous_flash"))));
        player.sendSystemMessage(prompt);
    }

    private static boolean tryConvertZelda(ServerPlayer player, ZSSPlayerData data, Villager villager, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(ZSSRegistries.FAIRY_OCARINA.get()) || villager.isBaby()) return false;
        QuestNpc zelda = convertVillager(player, villager, ZSSRegistries.ZELDA.get(), "Princess Zelda");
        if (zelda == null) return false;
        interactZelda(player, data, hand);
        tell(player, "zelda.converted");
        return true;
    }

    private static boolean tryConvertBarnes(ServerPlayer player, Villager villager, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.GUNPOWDER) || villager.isBaby()) return false;
        QuestNpc barnes = convertVillager(player, villager, ZSSRegistries.BARNES.get(), "Barnes");
        if (barnes == null) return false;
        held.shrink(1);
        tell(player, "barnes.open");
        return true;
    }

    private static boolean tryConvertOrca(ServerPlayer player, ZSSPlayerData data, Villager villager) {
        if (villager.isBaby() || !villager.getName().getString().equals("Orca")
                || !isItem(player.getMainHandItem(), "knights_crest")) return false;
        QuestNpc orca = convertVillager(player, villager, ZSSRegistries.ORCA.get(), "Orca");
        if (orca == null) return false;
        interactOrca(player, data, InteractionHand.MAIN_HAND);
        return true;
    }

    public static boolean isCursedMan(Villager villager) {
        return !villager.isBaby() && villager.hasCustomName() && villager.getCustomName().getString().equals("Cursed Man");
    }

    private static QuestNpc convertVillager(ServerPlayer player, Villager villager,
                                             net.minecraft.world.entity.EntityType<QuestNpc> type, String name) {
        QuestNpc npc = type.create(player.serverLevel());
        if (npc == null) return null;
        npc.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        npc.setCustomName(Component.literal(name));
        if (!player.serverLevel().addFreshEntity(npc)) return null;
        villager.discard();
        return npc;
    }

    private static QuestNpc nearestNpc(ServerPlayer player, QuestNpc.Role role) {
        return player.serverLevel().getEntitiesOfClass(QuestNpc.class, player.getBoundingBox().inflate(4.0D),
                npc -> npc.role() == role).stream().findFirst().orElse(null);
    }

    private static boolean isBiggoronTarget(Entity target) {
        return BIGGORON_TRADES.stream().anyMatch(trade -> matchesTarget(target, trade));
    }

    private static int matchingBiggoronTrade(ItemStack held, Entity target) {
        for (int i = 0; i < BIGGORON_TRADES.size(); i++) {
            BiggoronTrade trade = BIGGORON_TRADES.get(i);
            if (isItem(held, trade.input()) && matchesTarget(target, trade)) return i;
        }
        return -1;
    }

    private static boolean matchesTarget(Entity target, BiggoronTrade trade) {
        if (!target.getName().getString().equals(trade.targetName())) return false;
        if (trade.goron()) return target instanceof QuestNpc npc && npc.role() == QuestNpc.Role.GORON;
        if (!(target instanceof Villager villager) || villager.getVillagerData().getProfession() != trade.profession()) return false;
        return !trade.baby() || villager.isBaby();
    }

    private static boolean isStrongInstrument(ItemStack stack) {
        return stack.getItem() instanceof InstrumentItem instrument && instrument.songStrength() >= 5;
    }

    private static void giveCompletionReward(ServerPlayer player, ResourceLocation quest) {
        if (quest.equals(ZSSContentIds.ZELDA_LETTER)) give(player, stack("zeldas_letter", 1));
        else if (quest.equals(ZSSContentIds.PENDANTS)) {
            ItemStack forestKey = stack("big_key", 1);
            forestKey.getOrCreateTag().putString("dungeon", id("forest").toString());
            give(player, forestKey);
        } else if (quest.equals(ZSSContentIds.MASTER_SWORD_QUEST)) give(player, new ItemStack(ZSSRegistries.OCARINA_OF_TIME.get()));
        else if (quest.equals(ZSSContentIds.LIGHT_ARROWS)) give(player, stack("light_arrow", 8));
        else if (quest.equals(ZSSContentIds.MASK_SALES)) give(player, stack("mask_of_truth", 1));
        else if (quest.equals(ZSSContentIds.BIGGORON_SWORD_QUEST)) give(player, stack("biggoron_sword", 1));
    }

    private static void returnPendants(ServerPlayer player) {
        give(player, new ItemStack(ZSSRegistries.PENDANT_WISDOM.get()));
        give(player, new ItemStack(ZSSRegistries.PENDANT_COURAGE.get()));
        give(player, new ItemStack(ZSSRegistries.PENDANT_POWER.get()));
    }

    private static void returnEscrow(ServerPlayer player, ZSSPlayerData data) {
        ItemStack stack = data.takeEscrowOcarina();
        if (!stack.isEmpty()) give(player, stack);
    }

    private static ZSSPlayerData.QuestProgress progress(ZSSPlayerData data, ResourceLocation id) {
        return data.quests().getOrDefault(id, new ZSSPlayerData.QuestProgress(QuestStates.NOT_STARTED, 0, 0L, Map.of()));
    }

    private static void start(ZSSPlayerData data, ResourceLocation id, long now) {
        if (progress(data, id).state().equals(QuestStates.NOT_STARTED)) set(data, id, QuestStates.STARTED, 0, now);
    }

    private static void set(ZSSPlayerData data, ResourceLocation quest, ResourceLocation state, int step, long time) {
        data.setQuestProgress(quest, state, step, time, Map.of());
    }

    private static boolean consume(ServerPlayer player, Item item, int amount) {
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

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void addMaskOffer(MerchantOffers offers, String mask, int emeralds) {
        offers.add(new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), stack(mask, 1), 99, 1, 0.0F));
    }

    private static ItemStack stack(String path, int count) {
        return new ItemStack(ZSSRegistries.getItem(path), count);
    }

    private static boolean isItem(ItemStack stack, String path) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && key.equals(id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }

    private static void tell(ServerPlayer player, String path, Object... args) {
        player.sendSystemMessage(Component.translatable("quest." + ZeldaSwordSkills_Remastered.MOD_ID + "." + path, args));
    }

    private static void sync(ServerPlayer player) {
        ZSSNetwork.syncPlayerData(player);
    }

    private record MaskTrade(String item, int buyPrice, int salePrice) {
    }

    private record BiggoronTrade(String input, String output, String targetName, VillagerProfession profession,
                                 boolean baby, boolean goron) {
    }
}
