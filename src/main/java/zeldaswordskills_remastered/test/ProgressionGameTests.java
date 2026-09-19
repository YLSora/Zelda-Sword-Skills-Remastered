package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.item.BombBagItem;
import zeldaswordskills_remastered.item.PedestalItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantments;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class ProgressionGameTests {
    private ProgressionGameTests() {}

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void staticRecipesMatchAndUnlock(GameTestHelper helper) {
        var player = player(helper);
        String[][] cases = {
                {"fairy_ocarina", "1", "", "clay_ball", "", "clay_ball", "sugar_cane", "clay_ball", "", "", ""},
                {"wooden_hammer", "1", "oak_log", "birch_log", "spruce_log", "", "stick", "", "", "stick", ""},
                {"book_of_mudora", "2", "z:book_of_mudora", "book", "feather", "ink_sac", "", "", "", "", ""},
                {"cobblestone_from_throwing_rocks", "1", "z:throwing_rock", "z:throwing_rock", "z:throwing_rock", "z:throwing_rock", "z:throwing_rock", "z:throwing_rock", "z:throwing_rock", "z:throwing_rock", "z:throwing_rock"},
                {"beam_wooden", "1", "oak_planks", "", "", "birch_planks", "", "", "spruce_planks", "", ""},
                {"gossip_stone", "1", "", "stone", "", "stone", "z:fairy_ocarina", "stone", "", "stone", ""},
                {"hook_target", "1", "", "iron_bars", "", "stone", "redstone", "stone", "", "stone", ""},
                {"hook_target_all", "1", "stone", "iron_bars", "stone", "iron_bars", "redstone", "iron_bars", "stone", "iron_bars", "stone"},
                {"bomb_arrow", "1", "z:standard_bomb", "arrow", "", "", "", "", "", "", ""},
                {"fire_bomb_arrow", "1", "z:fire_bomb", "arrow", "", "", "", "", "", "", ""},
                {"water_bomb_arrow", "1", "z:water_bomb", "arrow", "", "", "", "", "", "", ""},
                {"ceramic_jar", "8", "brick", "", "brick", "brick", "", "brick", "", "brick", ""}
        };
        var manager = helper.getLevel().getRecipeManager();
        for (var entry : cases) {
            var grid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
            for (int slot = 0; slot < 9; slot++) {
                String ingredient = entry[slot + 2];
                if (ingredient.isEmpty()) continue;
                var id = new ResourceLocation(ingredient.startsWith("z:")
                        ? "zeldaswordskills_remastered:" + ingredient.substring(2) : "minecraft:" + ingredient);
                grid.setItem(slot, new ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id)));
            }
            var id = new ResourceLocation(ZeldaSwordSkills_Remastered.MOD_ID, entry[0]);
            var recipe = manager.getRecipeFor(RecipeType.CRAFTING, grid, helper.getLevel()).orElseThrow();
            helper.assertTrue(recipe.getId().equals(id), "Wrong recipe matched for " + id);
            var result = recipe.assemble(grid, helper.getLevel().registryAccess());
            var expected = entry[0].equals("cobblestone_from_throwing_rocks")
                    ? Items.COBBLESTONE : ZSSRegistries.getItem(entry[0]);
            helper.assertTrue(result.is(expected) && result.getCount() == Integer.parseInt(entry[1]),
                    "Wrong recipe output or quantity for " + id);
            var advancement = helper.getLevel().getServer().getAdvancements().getAllAdvancements().stream()
                    .filter(value -> value.getRewards().getRecipes().length == 1
                            && value.getRewards().getRecipes()[0].equals(id)).findFirst();
            helper.assertTrue(advancement.isPresent(), "Missing vanilla recipe unlock advancement for " + id);
            grid.setItem(0, new ItemStack(Items.BEDROCK));
            helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Recipe accepted an extra or incorrect ingredient: " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void magicContainerConsumptionAndPersistence(GameTestHelper helper) {
        var player = player(helper);
        var data = ZSSCapabilities.get(player).orElseThrow(IllegalStateException::new);
        data.setMagic(10, 100);
        var stack = container(2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(stack.getUseAnimation() == UseAnim.DRINK && stack.getUseDuration() == 32,
                "Magic container must use the 32-tick drinking animation");
        stack.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem() && stack.getCount() == 2 && data.maxMagic() == 100,
                "Starting a drink must not consume or increase magic");
        player.releaseUsingItem();
        helper.assertTrue(stack.getCount() == 2 && data.currentMagic() == 10 && data.maxMagic() == 100
                        && player.getInventory().countItem(Items.GLASS_BOTTLE) == 0,
                "Canceled drinking changed magic or created a bottle");

        var result = stack.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(result == stack && stack.getCount() == 1 && data.maxMagic() == 110
                        && data.currentMagic() == 110 && player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                "Completed drink must consume exactly one container, add 50 capacity, refill and return one bottle");
        result = stack.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(result.is(Items.GLASS_BOTTLE) && stack.isEmpty()
                        && data.currentMagic() == 120 && data.maxMagic() == 120,
                "Last container must be replaced with a bottle");
        var loaded = new ZSSPlayerData();
        loaded.load(data.save());
        helper.assertTrue(loaded.maxMagic() == 120 && loaded.currentMagic() == 120,
                "Drunk capacity and refill did not survive saving and loading");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void magicContainerCapAndCreative(GameTestHelper helper) {
        var player = player(helper);
        var data = ZSSCapabilities.get(player).orElseThrow(IllegalStateException::new);
        float maximum = ZSSConfig.SERVER.maximumMagic.get().floatValue();
        data.setMagic(0, maximum - 5);
        var stack = container(3);
        stack.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(data.maxMagic() == maximum && data.currentMagic() == maximum && stack.getCount() == 2,
                "Container exceeded the configured capacity cap");
        stack.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(stack.getCount() == 2 && player.getInventory().countItem(Items.GLASS_BOTTLE) == 1,
                "Full capacity and magic must not consume a container or generate a bottle");
        data.setMagic(maximum - 10, maximum);
        stack.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(data.currentMagic() == maximum && stack.getCount() == 1
                        && player.getInventory().countItem(Items.GLASS_BOTTLE) == 2,
                "A capped player must still be able to consume a container to refill");
        player.getAbilities().instabuild = true;
        data.setMagic(0, 100);
        stack.finishUsingItem(helper.getLevel(), player);
        helper.assertTrue(stack.getCount() == 1 && data.maxMagic() == 110 && data.currentMagic() == 110
                        && player.getInventory().countItem(Items.GLASS_BOTTLE) == 2,
                "Creative drinking must grant capacity without consuming items or producing bottles");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void magicContainerPickupDoesNotDrink(GameTestHelper helper) {
        var player = player(helper);
        var data = ZSSCapabilities.get(player).orElseThrow(IllegalStateException::new);
        data.setMagic(10, 100);
        var dropped = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), container(2));
        dropped.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(dropped);
        dropped.playerTouch(player);
        helper.assertTrue(dropped.isRemoved() && player.getInventory().countItem(ZSSRegistries.getItem("magic_container")) == 2,
                "Picking up containers must transfer the entire stack into inventory");
        helper.assertTrue(data.currentMagic() == 10 && data.maxMagic() == 100
                        && player.getInventory().countItem(Items.GLASS_BOTTLE) == 0,
                "Picking up containers must not drink them");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft")
    public static void statefulRecipesPreserveAndConsumeState(GameTestHelper helper) {
        var player = player(helper);
        var manager = helper.getLevel().getRecipeManager();

        ItemStack first = new ItemStack(ZSSRegistries.getItem("bomb_bag"));
        ItemStack standard = new ItemStack(ZSSRegistries.getItem("standard_bomb"), 3);
        ItemStack water = new ItemStack(ZSSRegistries.getItem("water_bomb"), 2);
        BombBagItem.add(first, standard);
        BombBagItem.add(first, water);
        ItemStack second = new ItemStack(ZSSRegistries.getItem("bomb_bag"));
        ItemStack fire = new ItemStack(ZSSRegistries.getItem("fire_bomb"), 4);
        BombBagItem.add(second, fire);

        var combineGrid = new TransientCraftingContainer(player.inventoryMenu, 2, 1);
        combineGrid.setItem(0, first);
        combineGrid.setItem(1, second);
        var combine = manager.getRecipeFor(RecipeType.CRAFTING, combineGrid, helper.getLevel()).orElseThrow();
        helper.assertTrue(combine.getId().equals(new ResourceLocation(ZeldaSwordSkills_Remastered.MOD_ID, "combine_bomb_bag")),
                "Mixed bomb bags did not select the combine recipe");
        ItemStack combined = combine.assemble(combineGrid, helper.getLevel().registryAccess());
        helper.assertTrue(BombBagItem.capacity(combined) == 20
                        && BombBagItem.getCount(combined, "standard_bomb") == 3
                        && BombBagItem.getCount(combined, "water_bomb") == 2
                        && BombBagItem.getCount(combined, "fire_bomb") == 4,
                "Combining bags changed capacity or bomb counts");
        BombBagItem.setCapacity(first, 40);
        BombBagItem.setCapacity(second, 20);
        helper.assertTrue(!combine.matches(combineGrid, helper.getLevel()), "Bomb bags combined beyond maximum capacity");

        combined.getOrCreateTag().putString(BombBagItem.SELECTED_TAG, "water_bomb");
        var arrowGrid = new TransientCraftingContainer(player.inventoryMenu, 2, 1);
        arrowGrid.setItem(0, combined);
        arrowGrid.setItem(1, new ItemStack(Items.ARROW, 5));
        var arrowRecipe = manager.getRecipeFor(RecipeType.CRAFTING, arrowGrid, helper.getLevel()).orElseThrow();
        ItemStack bombArrow = arrowRecipe.assemble(arrowGrid, helper.getLevel().registryAccess());
        ItemStack remainingBag = arrowRecipe.getRemainingItems(arrowGrid).get(0);
        helper.assertTrue(bombArrow.is(ZSSRegistries.getItem("water_bomb_arrow"))
                        && BombBagItem.getCount(remainingBag, "water_bomb") == 1
                        && BombBagItem.getCount(remainingBag, "standard_bomb") == 3
                        && BombBagItem.getCount(remainingBag, "fire_bomb") == 4,
                "Bomb-arrow recipe ignored selection or consumed the wrong bomb");
        arrowGrid.setItem(1, new ItemStack(Items.BEDROCK));
        helper.assertTrue(!arrowRecipe.matches(arrowGrid, helper.getLevel()), "Bomb-arrow recipe accepted an invalid ingredient");

        ItemStack leggings = new ItemStack(ZSSRegistries.getItem("goron_tunic_leggings"));
        leggings.setDamageValue(7);
        leggings.enchant(Enchantments.UNBREAKING, 2);
        leggings.setHoverName(Component.literal("Kept Name"));
        var dyeGrid = new TransientCraftingContainer(player.inventoryMenu, 2, 1);
        dyeGrid.setItem(0, leggings);
        dyeGrid.setItem(1, new ItemStack(Items.GREEN_DYE));
        var dyeRecipe = manager.getRecipeFor(RecipeType.CRAFTING, dyeGrid, helper.getLevel()).orElseThrow();
        ItemStack dyed = dyeRecipe.assemble(dyeGrid, helper.getLevel().registryAccess());
        helper.assertTrue(dyeRecipe.getId().equals(new ResourceLocation(ZeldaSwordSkills_Remastered.MOD_ID, "hero_from_goron"))
                        && dyed.is(ZSSRegistries.getItem("hero_tunic_leggings")) && dyed.getDamageValue() == 7
                        && dyed.getEnchantmentLevel(Enchantments.UNBREAKING) == 2
                        && dyed.getHoverName().getString().equals("Kept Name"),
                "Tunic dye recipe did not preserve durability, enchantments and name");

        ItemStack pedestal = PedestalItem.withInsertedSword(ZSSRegistries.getItem("pedestal"),
                new ItemStack(ZSSRegistries.getItem("master_sword")));
        var pedestalGrid = new TransientCraftingContainer(player.inventoryMenu, 3, 3);
        for (int slot = 0; slot < 9; slot++) pedestalGrid.setItem(slot,
                slot == 4 ? pedestal : new ItemStack(Items.QUARTZ_BLOCK));
        helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, pedestalGrid, helper.getLevel()).isEmpty(),
                "Legacy pedestal copy recipe duplicated a stored sword");
        helper.succeed();
    }

    private static ItemStack container(int count) {
        return new ItemStack(ZSSRegistries.getItem("magic_container"), count);
    }

    private static FakePlayer player(GameTestHelper helper) {
        return new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Progress]"));
    }
}
