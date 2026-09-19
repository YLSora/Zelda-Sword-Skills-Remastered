package zeldaswordskills_remastered.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.entity.NaviService;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSCommands {
    private ZSSCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zss")
                .then(Commands.literal("hearts")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                        .executes(context -> setHearts(context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count"))))))
                .then(Commands.literal("skill")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("skill", ResourceLocationArgument.id())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 100))
                                                .executes(context -> setSkill(
                                                        context.getSource().getPlayerOrException(),
                                                        ResourceLocationArgument.getId(context, "skill"),
                                                        IntegerArgumentType.getInteger(context, "level")))))))
                .then(Commands.literal("song")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("learn")
                                .then(Commands.argument("song", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ZSSContentIds.SONGS, builder))
                                        .executes(context -> learnSong(context.getSource().getPlayerOrException(),
                                                ResourceLocationArgument.getId(context, "song")))))
                        .then(Commands.literal("forget")
                                .then(Commands.argument("song", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> {
                                            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                                                return ZSSCapabilities.get(player)
                                                        .map(data -> SharedSuggestionProvider.suggestResource(data.songs(), builder))
                                                        .orElseGet(() -> builder.buildFuture());
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(context -> forgetSong(context.getSource().getPlayerOrException(),
                                                ResourceLocationArgument.getId(context, "song"))))))
                .then(Commands.literal("quest")
                        .requires(source -> source.hasPermission(2))
                        .then(questOperation("achieve", true))
                        .then(questOperation("reset", false))
                        .then(Commands.literal("resetall")
                                .executes(context -> resetAllQuests(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> resetAllQuests(context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("orca")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.literal("choose")
                                .then(Commands.literal("super_spin_attack")
                                        .executes(context -> chooseOrcaSkill(context.getSource().getPlayerOrException(),
                                                ZSSContentIds.SUPER_SPIN_ATTACK)))
                                .then(Commands.literal("continuous_flash")
                                        .executes(context -> chooseOrcaSkill(context.getSource().getPlayerOrException(),
                                                ZSSContentIds.CONTINUOUS_FLASH)))))
                .then(Commands.literal("navi")
                        .requires(source -> source.hasPermission(0))
                        .then(Commands.literal("call")
                                .executes(context -> callNavi(context.getSource().getPlayerOrException())))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> resetNavi(EntityArgument.getPlayer(context, "player")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> questOperation(String name, boolean achieve) {
        return Commands.literal(name)
                .then(Commands.argument("quest", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ZSSContentIds.QUESTS, builder))
                        .executes(context -> editQuest(context.getSource(), context.getSource().getPlayerOrException(),
                                ResourceLocationArgument.getId(context, "quest"), achieve))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> editQuest(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                        ResourceLocationArgument.getId(context, "quest"), achieve))));
    }

    private static int editQuest(CommandSourceStack source, ServerPlayer player, ResourceLocation quest, boolean achieve) {
        if (!ZSSContentIds.QUESTS.contains(quest)) {
            source.sendFailure(Component.translatable("command.zeldaswordskills_remastered.quest.unknown", quest));
            return 0;
        }
        boolean changed = achieve ? QuestService.achieve(player, quest) : QuestService.reset(player, quest);
        if (!changed) {
            source.sendFailure(Component.translatable("command.zeldaswordskills_remastered.quest.unavailable", player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(achieve
                ? "command.zeldaswordskills_remastered.quest.achieve" : "command.zeldaswordskills_remastered.quest.reset",
                quest, player.getDisplayName()), true);
        return 1;
    }

    private static int resetAllQuests(CommandSourceStack source, ServerPlayer player) {
        if (!QuestService.resetAll(player)) {
            source.sendFailure(Component.translatable("command.zeldaswordskills_remastered.quest.unavailable", player.getDisplayName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.zeldaswordskills_remastered.quest.resetall", player.getDisplayName()), true);
        return 1;
    }

    private static int setSkill(ServerPlayer player, ResourceLocation skill, int level) {
        if (!ZSSContentIds.SKILLS.contains(skill)) {
            player.sendSystemMessage(Component.literal("Unknown Zelda skill: " + skill));
            return 0;
        }
        ZSSCapabilities.get(player).ifPresent(data -> {
            data.setSkillLevel(skill, level);
            AdvancedSwordSkills.tick(player, data);
            if (level == 0 && skill.equals(ZSSContentIds.SWORD_BASIC)) {
                data.combat().clearTarget();
                data.combat().finishCombo();
                ZSSNetwork.syncCombatState(player, data.combat());
            }
            ZSSNetwork.syncPlayerData(player);
        });
        player.sendSystemMessage(Component.literal("Set " + skill + " to level " + level));
        return 1;
    }

    private static int chooseOrcaSkill(ServerPlayer player, ResourceLocation skill) {
        if (!QuestService.chooseOrcaSkill(player, skill)) {
            player.sendSystemMessage(Component.literal("奥卡的技能选择已失效或尚未开放。"));
            return 0;
        }
        return 1;
    }

    private static int setHearts(ServerPlayer player, int count) {
        int maximum = zeldaswordskills_remastered.config.ZSSConfig.SERVER.maximumHeartContainers.get();
        if (count > maximum) {
            player.sendSystemMessage(Component.translatable("command.zeldaswordskills_remastered.hearts.maximum", maximum));
            return 0;
        }
        var data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return 0;
        data.setSkillLevel(ZSSContentIds.BONUS_HEART, count);
        AdvancedSwordSkills.tickBonusHearts(player, data);
        ZSSNetwork.syncPlayerData(player);
        player.sendSystemMessage(Component.translatable("command.zeldaswordskills_remastered.hearts.set", count));
        return 1;
    }

    private static int learnSong(ServerPlayer player, ResourceLocation song) {
        if (!ZSSContentIds.SONGS.contains(song) || song.equals(ZSSContentIds.SCARECROW)) {
            player.sendSystemMessage(Component.translatable("command.zeldaswordskills_remastered.song.unavailable", song));
            return 0;
        }
        ZSSCapabilities.get(player).ifPresent(data -> {
            if (data.learnSong(song)) ZSSAdvancementService.songLearned(player, song, data.songs().size());
            ZSSNetwork.syncPlayerData(player);
        });
        player.sendSystemMessage(Component.translatable("command.zeldaswordskills_remastered.song.learned", song));
        return 1;
    }

    private static int forgetSong(ServerPlayer player, ResourceLocation song) {
        if (!ZSSContentIds.SONGS.contains(song)) {
            player.sendSystemMessage(Component.translatable("command.zeldaswordskills_remastered.song.unknown", song));
            return 0;
        }
        ZSSCapabilities.get(player).ifPresent(data -> {
            if (song.equals(ZSSContentIds.SCARECROW)) data.clearScarecrowMelody();
            else data.forgetSong(song);
            ZSSNetwork.syncPlayerData(player);
        });
        player.sendSystemMessage(Component.translatable("command.zeldaswordskills_remastered.song.forgotten", song));
        return 1;
    }

    private static int callNavi(ServerPlayer player) {
        boolean called = NaviService.call(player);
        player.sendSystemMessage(Component.translatable(called
                ? "command.zeldaswordskills_remastered.navi.call" : "message.zeldaswordskills_remastered.navi_no_space"));
        return called ? 1 : 0;
    }

    private static int resetNavi(ServerPlayer target) {
        boolean hadNavi = NaviCreature.savedNaviId(target).isPresent();
        NaviService.forget(target);
        target.sendSystemMessage(Component.translatable("command.zeldaswordskills_remastered.navi.reset"));
        return hadNavi ? 1 : 0;
    }
}
