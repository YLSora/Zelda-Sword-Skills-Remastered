package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;
import zeldaswordskills_remastered.item.ZeldaCombatItems;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElementArrowGameTests {
    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void elementalArrowsRespectUpgradesChargeAndMagic(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "arrow_test"));
        player.setPos(helper.absoluteVec(new Vec3(4, 10, 4)));
        var data = ZSSCapabilities.get(player).orElseThrow(() -> new AssertionError("Missing player data"));
        ItemStack bow = new ItemStack(ZSSRegistries.getItem("hero_bow"));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        for (String id : new String[]{"fire_arrow", "ice_arrow", "light_arrow"}) {
            for (int upgrade = 1; upgrade <= 3; upgrade++) {
                for (int charge : new int[]{0, 20}) {
                    for (int magic : new int[]{0, 100}) {
                        bow.getOrCreateTag().putInt("fairy_level", upgrade);
                        ItemStack ammo = new ItemStack(ZSSRegistries.getItem(id), 2);
                        player.setItemInHand(InteractionHand.OFF_HAND, ammo);
                        data.setMagic(magic, 100);
                        var kind = ((ZeldaCombatItems.ElementArrow) ammo.getItem()).kind();
                        int required = id.equals("light_arrow") ? 3 : 2;
                        int cost = id.equals("light_arrow") ? 20 : 10;
                        boolean fires = upgrade >= required && charge == 20 && magic >= cost;
                        bow.getItem().releaseUsing(bow, helper.getLevel(), player, bow.getUseDuration() - charge);
                        var arrows = helper.getLevel().getEntitiesOfClass(ZeldaArrow.class, player.getBoundingBox().inflate(2), arrow -> arrow.getOwner() == player);
                        helper.assertTrue(arrows.size() == (fires ? 1 : 0), "Wrong projectile count: " + id + "/" + upgrade + "/" + charge + "/" + magic);
                        helper.assertTrue(ammo.getCount() == (fires ? 1 : 2), "Incorrect ammo consumption");
                        helper.assertTrue(data.currentMagic() == magic - (fires ? cost : 0), "Incorrect magic consumption");
                        for (var arrow : arrows) {
                            helper.assertTrue(arrow.kind() == kind && arrow.getDeltaMovement().lengthSqr() > 0, "Wrong or stationary arrow");
                            arrow.discard();
                        }
                    }
                }
            }
        }
        helper.succeed();
    }
}
