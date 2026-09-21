package zeldaswordskills_remastered.test;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import io.netty.buffer.Unpooled;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;
import zeldaswordskills_remastered.item.ZeldaCombatItems;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class PresentationGameTests {
    private PresentationGameTests() {
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void monstersTakeFallDamageExceptDarknutsAndKeese(GameTestHelper helper) {
        for (var type : ZSSRegistries.LEGACY_CREATURE_TYPES) {
            var creature = type.get().create(helper.getLevel());
            if (creature.kind().family() == zeldaswordskills_remastered.entity.LegacyCreature.Family.FAIRY) continue;
            float before = creature.getHealth();
            boolean immune = creature instanceof zeldaswordskills_remastered.entity.DarknutCreature
                    || creature instanceof zeldaswordskills_remastered.entity.KeeseCreature;
            creature.causeFallDamage(5, 1, creature.damageSources().fall());
            helper.assertTrue(immune ? creature.getHealth() == before : creature.getHealth() < before,
                    "Incorrect fall damage rule: " + type.getId());
            creature.discard();
        }
        var desert = ZSSRegistries.DESERT_BOSS.get().create(helper.getLevel());
        float before = desert.getHealth();
        desert.causeFallDamage(5, 1, desert.damageSources().fall());
        helper.assertTrue(desert.getHealth() < before, "Desert boss must take fall damage");
        desert.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void shieldsUseVanillaBlocking(GameTestHelper helper) {
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ShieldTest"));
        for (String id : new String[]{"deku_shield", "hylian_shield", "mirror_shield"}) {
            var stack = new net.minecraft.world.item.ItemStack(ZSSRegistries.getItem(id));
            owner.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack);
            stack.getItem().use(helper.getLevel(), owner, net.minecraft.world.InteractionHand.OFF_HAND);
            helper.assertTrue(owner.isUsingItem() && owner.getUseItem() == stack
                            && stack.getUseAnimation() == net.minecraft.world.item.UseAnim.BLOCK
                            && !owner.isBlocking(),
                    "Shield must start vanilla offhand use with its normal blocking delay: " + id);
            owner.stopUsingItem();
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void zssItemsRespectTheirDurability(GameTestHelper helper) {
        var owner = EntityType.ZOMBIE.create(helper.getLevel());
        for (var entry : ZSSRegistries.ITEMS.getEntries()) {
            var stack = new net.minecraft.world.item.ItemStack(entry.get());
            if (stack.isDamageableItem()) {
                int maximum = stack.getMaxDamage();
                stack.hurtAndBreak(1, owner, entity -> {});
                helper.assertTrue(stack.getDamageValue() == 1, "Equipment did not lose durability: " + entry.getId());
                stack = net.minecraft.world.item.ItemStack.of(stack.save(new CompoundTag()));
                helper.assertTrue(stack.getDamageValue() == 1 && stack.getMaxDamage() == maximum,
                        "Equipment durability did not survive reload: " + entry.getId());
                stack.hurtAndBreak(maximum, owner, entity -> {});
                helper.assertTrue(stack.isEmpty(), "Equipment did not break: " + entry.getId());
                continue;
            }
            // Damage may already be present on a saved stack or supplied by a command.
            stack.getOrCreateTag().putInt("Damage", 10000);
            stack = net.minecraft.world.item.ItemStack.of(stack.save(new CompoundTag()));
            int damage = stack.getDamageValue();
            helper.assertTrue(!stack.isDamageableItem() && !stack.isBarVisible(),
                    "ZSS item still exposes durability: " + entry.getId());
            stack.hurtAndBreak(10000, owner, entity -> {
                throw new AssertionError("ZSS item broke: " + entry.getId());
            });
            helper.assertTrue(stack.getCount() == 1 && stack.getDamageValue() == damage,
                    "Durability damage altered a ZSS item: " + entry.getId());
        }
        var vanilla = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
        vanilla.hurtAndBreak(10000, owner, entity -> {});
        helper.assertTrue(vanilla.isEmpty(), "Vanilla durability was changed");
        for (String id : new String[]{"master_sword", "wooden_hammer", "hero_tunic_chestplate", "hero_bow"}) {
            helper.assertTrue(new net.minecraft.world.item.ItemStack(ZSSRegistries.getItem(id)).isEnchantable(),
                    "Unbreakable equipment lost enchanting: " + id);
        }
        owner.discard();
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void spiritCrystalUseConsumesDurability(GameTestHelper helper) {
        var owner = net.minecraftforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "CrystalTest"));
        owner.getAbilities().instabuild = false;
        for (String id : new String[]{"din_crystal", "farore_crystal", "nayru_crystal"}) {
            var stack = new net.minecraft.world.item.ItemStack(ZSSRegistries.getItem(id));
            stack.setDamageValue(255);
            owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            owner.setShiftKeyDown(id.equals("farore_crystal"));
            owner.getCooldowns().removeCooldown(stack.getItem());
            var result = stack.getItem().use(helper.getLevel(), owner, net.minecraft.world.InteractionHand.MAIN_HAND);
            helper.assertTrue(result.getResult().consumesAction()
                            && (id.equals("farore_crystal") ? stack.getCount() == 1 && stack.getDamageValue() == 255 : stack.isEmpty()),
                    "Crystal use must consume durability, but setting a recall point must not: " + id);
        }
        owner.setShiftKeyDown(false);
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void arrowVariantsSurviveTrackingAndReload(GameTestHelper helper) {
        var owner = EntityType.ZOMBIE.create(helper.getLevel());
        for (var kind : ZeldaCombatItems.ArrowKind.values()) {
            var type = switch (kind) {
                case BOMB, FIRE_BOMB, WATER_BOMB -> ZSSRegistries.ARROW_BOMB.get();
                case FIRE, ICE, LIGHT -> ZSSRegistries.ARROW_ELEMENTAL.get();
            };
            var arrow = new ZeldaArrow(type, helper.getLevel(), owner, kind);
            var loaded = new ZeldaArrow(type, helper.getLevel());
            var tag = new CompoundTag();
            arrow.addAdditionalSaveData(tag);
            loaded.readAdditionalSaveData(tag);
            helper.assertTrue(loaded.kind() == kind, "Arrow variant lost on reload: " + kind);
            helper.assertTrue(tag.getString("kind").equals(kind.name().toLowerCase(java.util.Locale.ROOT)),
                    "Arrow kind must remain a string ID");
            var tracked = new ZeldaArrow(type, helper.getLevel());
            var values = loaded.getEntityData().getNonDefaultValues();
            if (values != null) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new ClientboundSetEntityDataPacket(loaded.getId(), values).write(buffer);
                    tracked.getEntityData().assignValues(new ClientboundSetEntityDataPacket(buffer).packedItems());
                } finally {
                    buffer.release();
                }
            }
            helper.assertTrue(tracked.kind() == kind, "Arrow variant lost in tracking packet: " + kind);
        }
        helper.succeed();
    }
}
