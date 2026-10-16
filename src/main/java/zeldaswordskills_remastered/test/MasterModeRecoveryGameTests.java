package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.config.MasterModeConfig;
import zeldaswordskills_remastered.registry.MusicDiscCatalog;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MasterModeRecoveryGameTests {
    @net.minecraft.gametest.framework.BeforeBatch(batch = "zssRecovery")
    public static void setupRecovery(net.minecraft.server.level.ServerLevel level) { setup(level); }

    @net.minecraft.gametest.framework.BeforeBatch(batch = "zssRecoveryInterrupt")
    public static void setupInterrupt(net.minecraft.server.level.ServerLevel level) { setup(level); }

    private static void setup(net.minecraft.server.level.ServerLevel level) {
        ConfigQuestGameTests.useInMemoryConfig(level);
        var config = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        MasterModeConfig.SPEC.correct(config);
        MasterModeConfig.SPEC.setConfig(config);
    }

    @net.minecraft.gametest.framework.AfterBatch(batch = "zssRecovery")
    public static void restoreRecovery(net.minecraft.server.level.ServerLevel level) { restore(level); }

    @net.minecraft.gametest.framework.AfterBatch(batch = "zssRecoveryInterrupt")
    public static void restoreInterrupt(net.minecraft.server.level.ServerLevel level) { restore(level); }

    private static void restore(net.minecraft.server.level.ServerLevel level) {
        ConfigQuestGameTests.restoreServerConfig(level);
        var config = net.minecraftforge.fml.config.ConfigTracker.INSTANCE.configSets().get(net.minecraftforge.fml.config.ModConfig.Type.SERVER)
                .stream().filter(candidate -> candidate.getSpec() == MasterModeConfig.SPEC).findFirst().orElseThrow();
        MasterModeConfig.SPEC.setConfig(config.getConfigData());
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssRecovery", timeoutTicks = 700)
    public static void recoveryWaitsRepeatsAndStopsAtBudget(GameTestHelper helper) {
        ZSSConfig.SERVER.masterMode.set(true);
        var mob = helper.spawn(EntityType.HUSK, new BlockPos(2, 2, 2));
        mob.setNoAi(true);
        mob.setInvulnerable(false);
        float max = mob.getMaxHealth();
        mob.hurt(mob.damageSources().generic(), max * .4F);
        float wounded = mob.getHealth();
        helper.runAtTickTime(199, () -> helper.assertTrue(mob.getHealth() == wounded, "Healed before ten seconds"));
        helper.runAtTickTime(202, () -> helper.assertTrue(Math.abs(mob.getHealth() - wounded - max * .05F) < .001F,
                "First recovery did not heal five percent"));
        helper.runAtTickTime(250, () -> helper.assertTrue(Math.abs(mob.getHealth() - wounded - max * .05F) < .001F,
                "Healed before three-second interval"));
        helper.runAtTickTime(570, () -> {
            helper.assertTrue(Math.abs(mob.getHealth() - (wounded + (max - wounded) * .75F)) < .001F,
                    "Recovery exceeded or failed to reach the 75% budget");
            mob.discard();
            ZSSConfig.SERVER.masterMode.set(false);
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssRecovery", timeoutTicks = 600)
    public static void recoveryAboveThreeQuartersCanReachFullHealth(GameTestHelper helper) {
        ZSSConfig.SERVER.masterMode.set(true);
        var high = helper.spawn(EntityType.HUSK, new BlockPos(2, 2, 4));
        var boundary = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 2));
        high.setNoAi(true);
        boundary.setNoAi(true);
        high.hurt(high.damageSources().generic(), 8);
        boundary.hurt(boundary.damageSources().generic(), 10);
        helper.runAtTickTime(500, () -> {
            helper.assertTrue(high.getHealth() == high.getMaxHealth(), "Enemy above 75% did not heal fully");
            helper.assertTrue(Math.abs(boundary.getHealth() - 37.5F) < .001F,
                    "Enemy at exactly 75% bypassed recovery budget");
            high.discard();
            boundary.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssRecoveryInterrupt", timeoutTicks = 500)
    public static void environmentalDamageRestartsWaitAndBlacklistBlocksRecovery(GameTestHelper helper) {
        ZSSConfig.SERVER.masterMode.set(true);
        var mob = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 4));
        mob.setNoAi(true);
        mob.hurt(mob.damageSources().generic(), 8);
        helper.runAtTickTime(150, () -> mob.hurt(mob.damageSources().magic(), 2));
        helper.runAtTickTime(340, () -> helper.assertTrue(mob.getHealth() == 30, "Environmental damage failed to restart wait"));
        helper.runAtTickTime(355, () -> {
            helper.assertTrue(mob.getHealth() == 32, "Recovery failed after restarted wait");
            MasterModeConfig.REGENERATION_BLACKLIST.set(java.util.List.of("minecraft:husk"));
        });
        helper.runAtTickTime(430, () -> {
            helper.assertTrue(mob.getHealth() == 32, "Blacklisted enemy regenerated");
            mob.discard();
            MasterModeConfig.REGENERATION_BLACKLIST.set(java.util.List.of());
            ZSSConfig.SERVER.masterMode.set(false);
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssConfigQuest")
    public static void disabledStructureLocateReturnsWithoutLoadingChunks(GameTestHelper helper) {
        boolean previous = ZSSConfig.SERVER.generateStructures.get();
        try {
            ZSSConfig.SERVER.generateStructures.set(false);
            var level = helper.getLevel();
            var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            var disabled = registry.holders().filter(h -> h.key().location().getNamespace().equals(ZeldaSwordSkills_Remastered.MOD_ID)).toList();
            helper.assertTrue(!disabled.isEmpty(), "No mod structures registered");
            int chunks = level.getChunkSource().getLoadedChunksCount();
            for (var structure : disabled) {
                var result = level.getChunkSource().getGenerator().findNearestMapStructure(level,
                        HolderSet.direct(structure), new BlockPos(900000, 64, 900000), 100, false);
                helper.assertTrue(result == null, "Disabled structure was located");
                int commandResult = level.getServer().getCommands().performPrefixedCommand(
                        level.getServer().createCommandSourceStack().withLevel(level)
                                .withPosition(new net.minecraft.world.phys.Vec3(900000, 64, 900000)).withSuppressedOutput(),
                        "locate structure " + structure.key().location());
                helper.assertTrue(commandResult == 0, "Locate command found a disabled structure");
            }
            helper.assertTrue(chunks == level.getChunkSource().getLoadedChunksCount(), "Disabled locate loaded chunks");
            var vanilla = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE,
                    net.minecraft.resources.ResourceLocation.parse("minecraft:village_plains")));
            var mixed = zeldaswordskills_remastered.worldgen.RoyalCastleHooks.filterLocateStructures(level,
                    HolderSet.direct(disabled.get(0), vanilla));
            helper.assertTrue(mixed.size() == 1 && mixed.contains(vanilla), "Mixed locate removed a vanilla structure");
        } finally {
            ZSSConfig.SERVER.generateStructures.set(previous);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void everyDiscPlaysThroughItsDeclaredDuration(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 1, 2));
        level.setBlockAndUpdate(pos, Blocks.JUKEBOX.defaultBlockState());
        var jukebox = (JukeboxBlockEntity) level.getBlockEntity(pos);
        for (var disc : MusicDiscCatalog.DISCS) {
            var item = (RecordItem) ZSSRegistries.getItem(disc.id());
            helper.assertTrue(item.getLengthInTicks() == disc.seconds() * 20, "Incorrect duration: " + disc.id());
            jukebox.setFirstItem(new ItemStack(item));
            for (int tick = 0; tick < item.getLengthInTicks(); tick++)
                JukeboxBlockEntity.playRecordTick(level, pos, level.getBlockState(pos), jukebox);
            helper.assertTrue(jukebox.isRecordPlaying(), "Disc stopped early: " + disc.id());
            for (int tick = 0; tick < 21; tick++)
                JukeboxBlockEntity.playRecordTick(level, pos, level.getBlockState(pos), jukebox);
            helper.assertTrue(!jukebox.isRecordPlaying(), "Disc did not finish: " + disc.id());
        }
        helper.succeed();
    }
}
