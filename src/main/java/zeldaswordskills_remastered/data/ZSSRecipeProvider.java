package zeldaswordskills_remastered.data;

import java.util.function.Consumer;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SpecialRecipeBuilder;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Static crafting from the frozen item recipes; stateful conversions have dedicated serializers. */
public final class ZSSRecipeProvider extends RecipeProvider {
    public ZSSRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> output) {
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, item("fairy_ocarina"))
                .pattern(" c ").pattern("crc")
                .define('c', Items.CLAY_BALL).define('r', Items.SUGAR_CANE)
                .unlockedBy("has_clay", has(Items.CLAY_BALL)).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, item("wooden_hammer"))
                .pattern("lll").pattern(" s ").pattern(" s ")
                .define('l', ItemTags.LOGS).define('s', Items.STICK)
                .unlockedBy("has_logs", has(ItemTags.LOGS)).save(output);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, item("book_of_mudora"), 2)
                .requires(item("book_of_mudora")).requires(Items.BOOK).requires(Items.FEATHER).requires(Items.INK_SAC)
                .unlockedBy("has_mudora", has(item("book_of_mudora"))).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, Items.COBBLESTONE)
                .pattern("rrr").pattern("rrr").pattern("rrr").define('r', item("throwing_rock"))
                .unlockedBy("has_rock", has(item("throwing_rock")))
                .save(output, "zeldaswordskills_remastered:cobblestone_from_throwing_rocks");
        ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS, item("beam_wooden"))
                .pattern("b").pattern("b").pattern("b").define('b', ItemTags.PLANKS)
                .unlockedBy("has_planks", has(ItemTags.PLANKS)).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, item("gossip_stone"))
                .pattern(" s ").pattern("sos").pattern(" s ")
                .define('s', Items.STONE).define('o', item("fairy_ocarina"))
                .unlockedBy("has_ocarina", has(item("fairy_ocarina"))).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, item("hook_target"))
                .pattern(" c ").pattern("bab").pattern(" b ")
                .define('a', Items.REDSTONE).define('b', Items.STONE).define('c', Items.IRON_BARS)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(output);
        ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, item("hook_target_all"))
                .pattern("bcb").pattern("cac").pattern("bcb")
                .define('a', Items.REDSTONE).define('b', Items.STONE).define('c', Items.IRON_BARS)
                .unlockedBy("has_redstone", has(Items.REDSTONE)).save(output);
        bombArrow(output, "standard_bomb", "bomb_arrow");
        bombArrow(output, "fire_bomb", "fire_bomb_arrow");
        bombArrow(output, "water_bomb", "water_bomb_arrow");
        dyeLeggings(output, "hero_from_goron"); dyeLeggings(output, "hero_from_zora");
        dyeLeggings(output, "goron_from_hero"); dyeLeggings(output, "goron_from_zora");
        dyeLeggings(output, "zora_from_hero"); dyeLeggings(output, "zora_from_goron");
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, item("ceramic_jar"), 8)
                .pattern("c c").pattern("c c").pattern(" c ").define('c', Items.BRICK)
                .unlockedBy("has_brick", has(Items.BRICK)).save(output);
    }

    private static void bombArrow(Consumer<FinishedRecipe> output, String bomb, String arrow) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.COMBAT, item(arrow))
                .requires(item(bomb)).requires(Items.ARROW)
                .unlockedBy("has_bomb", has(item(bomb))).save(output);
    }

    private static void dyeLeggings(Consumer<FinishedRecipe> output, String id) {
        SpecialRecipeBuilder.special(ZSSRegistries.TUNIC_DYE_RECIPE.get()).save(output, "zeldaswordskills_remastered:" + id);
    }

    private static Item item(String id) {
        return ZSSRegistries.getItem(id);
    }
}
