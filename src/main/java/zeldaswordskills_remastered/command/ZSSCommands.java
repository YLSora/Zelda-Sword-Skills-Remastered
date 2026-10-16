package zeldaswordskills_remastered.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.entity.NaviService;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;
import zeldaswordskills_remastered.combat.SkillAvailability;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSCommands {
    private ZSSCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("zss")
                .then(manaCommand("mana", false))
                .then(manaCommand("max_mana", true))
                .then(Commands.literal("hearts")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                        .executes(context -> setHearts(context.getSource().getPlayerOrException(),
                                                IntegerArgumentType.getInteger(context, "count"))))))
                .then(Commands.literal("skills")
                        .requires(source -> source.hasPermission(2))
                        .then(skillOperation("learn", true))
                        .then(skillOperation("forget", false))
                        .then(Commands.literal("set")
                                .then(Commands.argument("skill", ResourceLocationArgument.id())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(ZSSContentIds.SKILLS, builder))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0))
                                                .executes(context -> setSkill(
                                                        context.getSource(),
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

    private static LiteralArgumentBuilder<CommandSourceStack> manaCommand(String name, boolean maximum) {
        return Commands.literal(name)
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0F))
                                        .executes(context -> setMana(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                FloatArgumentType.getFloat(context, "value"), maximum))))
                        .then(Commands.literal(maximum ? "reset" : "full")
                                .executes(context -> restoreMana(context.getSource(), EntityArgument.getPlayer(context, "player"), maximum))));
    }

    private static int setMana(CommandSourceStack source, ServerPlayer player, float value, boolean maximum) {
        var data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return 0;
        float limit = maximum ? ZSSConfig.SERVER.maximumMagic.get().floatValue() : data.maxMagic();
        if (!Float.isFinite(value) || value < 0.0F || value > limit) {
            source.sendFailure(Component.translatable(maximum
                    ? "command.zeldaswordskills_remastered.max_mana.range" : "command.zeldaswordskills_remastered.mana.range",
                    player.getDisplayName(), limit, value));
            return 0;
        }
        data.setMagic(maximum ? data.currentMagic() : value, maximum ? value : data.maxMagic());
        ZSSNetwork.syncPlayerData(player);
        source.sendSuccess(() -> Component.translatable(maximum
                ? "command.zeldaswordskills_remastered.max_mana.set" : "command.zeldaswordskills_remastered.mana.set",
                player.getDisplayName(), maximum ? data.maxMagic() : data.currentMagic()), true);
        return 1;
    }

    private static int restoreMana(CommandSourceStack source, ServerPlayer player, boolean maximum) {
        var data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return 0;
        float value = maximum ? Math.min(ZSSConfig.SERVER.startingMagic.get().floatValue(), ZSSConfig.SERVER.maximumMagic.get().floatValue())
                : data.maxMagic();
        return setMana(source, player, value, maximum);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> skillOperation(String name, boolean learn) {
        return Commands.literal(name)
                .then(Commands.literal("all").executes(context -> setAllSkills(context.getSource(), learn)))
                .then(Commands.argument("skill", ResourceLocationArgument.id())
                        .suggests((context, builder) -> {
                            if (context.getSource().getEntity() instanceof ServerPlayer player) {
                                return ZSSCapabilities.get(player)
                                        .map(data -> SharedSuggestionProvider.suggestResource(ZSSContentIds.SKILLS.stream()
                                                .filter(skill -> learn ? data.skillLevel(skill) < data.skillMaximum(skill)
                                                        : data.skillLevel(skill) > 0), builder))
                                        .orElseGet(() -> builder.buildFuture());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> learn
                                ? learnSkill(context.getSource(), ResourceLocationArgument.getId(context, "skill"))
                                : setSkill(context.getSource(), ResourceLocationArgument.getId(context, "skill"), 0)));
    }

    private static int learnSkill(CommandSourceStack source, ResourceLocation skill) throws CommandSyntaxException {
        var data = ZSSCapabilities.get(source.getPlayerOrException()).orElse(null);
        return data == null ? 0 : setSkill(source, skill, data.skillMaximum(skill));
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

    private static int setSkill(CommandSourceStack source, ResourceLocation skill, int level) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!ZSSContentIds.SKILLS.contains(skill)) {
            source.sendFailure(Component.translatable("command.zeldaswordskills_remastered.skills.unknown", skill));
            return 0;
        }
        var data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return 0;
        int maximum = data.skillMaximum(skill);
        if (level < 0 || level > maximum) {
            source.sendFailure(Component.translatable("command.zeldaswordskills_remastered.skills.range", skill, maximum, level));
            return 0;
        }
        applySkillLevel(player, data, skill, level);
        AdvancedSwordSkills.tick(player, data);
        ZSSNetwork.syncPlayerData(player);
        source.sendSuccess(() -> Component.translatable("command.zeldaswordskills_remastered.skills.set", skill, level), false);
        return 1;
    }

    private static void applySkillLevel(ServerPlayer player, ZSSPlayerData data, ResourceLocation skill, int level) {
        if (level == 0) {
            boolean enabled = data.skillEnabled(skill);
            SkillAvailability.setEnabled(player, data, skill, false);
            if (enabled) data.setSkillEnabled(skill, true);
        }
        data.setSkillLevel(skill, level);
        if (level > 0) data.setSkillEnabled(skill, true);
    }

    private static int setAllSkills(CommandSourceStack source, boolean learn) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var data = ZSSCapabilities.get(player).orElse(null);
        if (data == null) return 0;
        for (var skill : ZSSContentIds.SKILL_ORDER) {
            applySkillLevel(player, data, skill, learn ? data.skillMaximum(skill) : 0);
        }
        AdvancedSwordSkills.tickBonusHearts(player, data);
        ZSSNetwork.syncPlayerData(player);
        source.sendSuccess(() -> Component.translatable(learn
                ? "command.zeldaswordskills_remastered.skills.learn_all"
                : "command.zeldaswordskills_remastered.skills.forget_all"), false);
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
