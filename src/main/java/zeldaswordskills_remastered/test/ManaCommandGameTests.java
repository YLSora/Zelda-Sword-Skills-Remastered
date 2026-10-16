package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;

import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManaCommandGameTests {
    private ManaCommandGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void manaCommandsTargetPlayersEnforceLimitsAndPersist(GameTestHelper helper) throws CommandSyntaxException {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mana_operator"));
        var target = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "mana_target"));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 20, 1))));
        target.setPos(player.position());
        var operatorData = ZSSCapabilities.get(player).orElseThrow(AssertionError::new);
        var data = ZSSCapabilities.get(target).orElseThrow(AssertionError::new);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        float configuredMaximum = ZSSConfig.SERVER.maximumMagic.get().floatValue();
        helper.assertTrue(helper.getLevel().addFreshEntity(target), "Could not add the command target to the level");
        try {
            var operatorBefore = operatorData.save();
            data.setMagic(10, 25);
            for (String command : new String[]{"zss mana @p[name=mana_target,distance=..8] set 12.5", "zss mana @p[name=mana_target,distance=..8] full",
                    "zss max_mana @p[name=mana_target,distance=..8] set 30", "zss max_mana @p[name=mana_target,distance=..8] reset"}) {
                var before = data.save();
                try {
                    dispatcher.execute(command, source.withPermission(0));
                    throw new AssertionError("Mana command bypassed operator permissions");
                } catch (CommandSyntaxException expected) {
                    helper.assertTrue(data.save().equals(before), "Unauthorized mana command changed player data");
                }
            }
            helper.assertTrue(dispatcher.execute("zss mana @p[name=mana_target,distance=..8] set 12.5", source) == 1
                            && data.currentMagic() == 12.5F && data.maxMagic() == 25,
                    "Current-mana command changed the wrong value");
            helper.assertTrue(dispatcher.execute("zss mana @p[name=mana_target,distance=..8] set 25.5", source) == 0
                            && data.currentMagic() == 12.5F && data.maxMagic() == 25,
                    "Current mana exceeded the player's capacity");
            helper.assertTrue(dispatcher.execute("zss mana @p[name=mana_target,distance=..8] full", source) == 1
                            && data.currentMagic() == 25 && data.maxMagic() == 25, "Full did not refill current mana");
            helper.assertTrue(dispatcher.execute("zss max_mana @p[name=mana_target,distance=..8] set 15.5", source) == 1
                            && data.currentMagic() == 15.5F && data.maxMagic() == 15.5F,
                    "Reducing capacity did not clamp current mana");
            helper.assertTrue(dispatcher.execute("zss max_mana @p[name=mana_target,distance=..8] set " + configuredMaximum, source) == 1
                            && data.currentMagic() == 15.5F && data.maxMagic() == configuredMaximum,
                    "Capacity change refilled mana or rejected the configuration limit");
            helper.assertTrue(dispatcher.execute("zss max_mana @p[name=mana_target,distance=..8] set " + (configuredMaximum + 1), source) == 0
                            && data.maxMagic() == configuredMaximum, "Capacity exceeded the server configuration");
            for (String prefix : new String[]{"zss mana @p[name=mana_target,distance=..8] set ", "zss max_mana @p[name=mana_target,distance=..8] set "}) {
                for (String value : new String[]{"-1", "NaN", "Infinity", "999999999999999999999999999999999999999999999999"}) {
                    var before = data.save();
                    try {
                        dispatcher.execute(prefix + value, source);
                        throw new AssertionError("Invalid mana value was accepted: " + value);
                    } catch (CommandSyntaxException expected) {
                        helper.assertTrue(data.save().equals(before), "Rejected number changed mana");
                    }
                }
            }
            dispatcher.execute("zss max_mana @p[name=mana_target,distance=..8] reset", source);
            float initialMaximum = Math.min(ZSSConfig.SERVER.startingMagic.get().floatValue(), configuredMaximum);
            helper.assertTrue(data.maxMagic() == initialMaximum && data.currentMagic() == Math.min(15.5F, initialMaximum),
                    "Reset did not restore starting capacity while preserving valid current mana");
            var loaded = new ZSSPlayerData();
            loaded.load(data.save());
            helper.assertTrue(loaded.currentMagic() == data.currentMagic() && loaded.maxMagic() == data.maxMagic(),
                    "Command-set mana did not survive saving and loading");
            dispatcher.execute("zss max_mana @p[name=mana_target,distance=..8] set 0", source);
            dispatcher.execute("zss mana @p[name=mana_target,distance=..8] full", source);
            helper.assertTrue(data.currentMagic() == 0 && data.maxMagic() == 0, "Zero capacity did not clear current mana");
            helper.assertTrue(operatorData.save().equals(operatorBefore), "Targeted command changed the operator's data");
        } finally {
            target.discard();
        }
        helper.succeed();
    }
}
