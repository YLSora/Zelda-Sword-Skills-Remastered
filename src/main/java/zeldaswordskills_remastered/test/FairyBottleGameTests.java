package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.item.SpecialItems;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FairyBottleGameTests {
    private FairyBottleGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void lethalDamageConsumesOneFairyAndGrantsResistance(GameTestHelper helper) {
        FakePlayer player = player(helper);
        try {
            ItemStack bottles = new ItemStack(ZSSRegistries.getItem("fairy_bottle"), 8);
            player.getInventory().setItem(12, bottles);
            helper.assertTrue(bottles.getMaxStackSize() == 8, "Fairy bottles must stack to eight");
            // FakePlayer ignores hurt/die; dispatch the same death hook used by ServerPlayer.
            player.setHealth(0);
            boolean cancelled = ForgeHooks.onLivingDeath(player, player.damageSources().fall());
            helper.assertTrue(cancelled && player.isAlive() && player.getHealth() == 10 && bottles.getCount() == 7,
                    "Lethal damage must restore ten health and consume exactly one fairy");
            var resistance = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
            helper.assertTrue(resistance != null && resistance.getAmplifier() == 9 && resistance.getDuration() == 100,
                    "Rescue must grant Resistance X for five seconds");
            helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                    "Rescue must return one glass bottle");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void killAndVoidCannotConsumeFairies(GameTestHelper helper) {
        FakePlayer player = player(helper);
        try {
            ItemStack bottles = new ItemStack(ZSSRegistries.getItem("fairy_bottle"), 8);
            player.setItemInHand(InteractionHand.OFF_HAND, bottles);
            for (DamageSource source : new DamageSource[]{player.damageSources().genericKill(), player.damageSources().fellOutOfWorld()}) {
                player.setHealth(0);
                helper.assertTrue(!ForgeHooks.onLivingDeath(player, source)
                                && player.getHealth() == 0 && bottles.getCount() == 8
                                && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                        "Kill/void must not revive, consume bottles or grant resistance");
            }
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void lastOffhandFairyBecomesGlassBottle(GameTestHelper helper) {
        FakePlayer player = player(helper);
        try {
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ZSSRegistries.getItem("fairy_bottle")));
            player.setHealth(0);
            boolean cancelled = ForgeHooks.onLivingDeath(player, player.damageSources().fall());
            helper.assertTrue(cancelled && player.getHealth() == 10 && player.getOffhandItem().is(Items.GLASS_BOTTLE),
                    "The last offhand fairy must be replaced by an empty bottle");
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void fullInventoryDropsOnlyTheReturnedContainer(GameTestHelper helper) {
        FakePlayer player = player(helper);
        try {
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
            }
            ItemStack bottles = new ItemStack(ZSSRegistries.getItem("fairy_bottle"), 8);
            player.setItemInHand(InteractionHand.OFF_HAND, bottles);
            player.setHealth(0);
            helper.assertTrue(SpecialItems.FairyBottle.releaseFairy(player, player.damageSources().fall())
                            && bottles.getCount() == 7, "Full inventory must consume only one fairy");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(2),
                    item -> item.getItem().is(Items.GLASS_BOTTLE));
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().getCount() == 1,
                    "Full inventory must drop the returned empty bottle exactly once");
            drops.forEach(ItemEntity::discard);
        } finally { player.discard(); }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void manualHealingConsumesOnlyOneBottle(GameTestHelper helper) {
        FakePlayer player = player(helper);
        try {
            ItemStack bottles = new ItemStack(ZSSRegistries.getItem("fairy_bottle"), 8);
            player.setItemInHand(InteractionHand.MAIN_HAND, bottles);
            player.setHealth(5);
            bottles.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            helper.assertTrue(player.getHealth() == 15 && bottles.getCount() == 7
                            && player.getInventory().countItem(Items.GLASS_BOTTLE) == 1
                            && !player.hasEffect(MobEffects.DAMAGE_RESISTANCE),
                    "Manual healing must consume one fairy without the rescue-only resistance");
        } finally { player.discard(); }
        helper.succeed();
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "fairy_test"));
        player.setPos(helper.absoluteVec(new Vec3(4.5D, 3, 4.5D)));
        return player;
    }
}
