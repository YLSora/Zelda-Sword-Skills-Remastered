package zeldaswordskills_remastered.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class HeartPieceRecipe extends CustomRecipe {
    public HeartPieceRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }

    @Override public boolean matches(CraftingContainer container, Level level) {
        int pieces = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!stack.is(ZSSRegistries.getItem("heart_piece"))) return false;
            pieces++;
        }
        return pieces == 4;
    }

    @Override public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        if (!matches(container, null)) return ItemStack.EMPTY;
        ItemStack orb = new ItemStack(ZSSRegistries.getItem("skill_orb"));
        orb.getOrCreateTag().putString(ProgressionItem.SKILL_TAG, ZSSContentIds.BONUS_HEART.toString());
        return orb;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 4; }
    @Override public RecipeSerializer<?> getSerializer() { return ZSSRegistries.HEART_PIECE_RECIPE.get(); }
}
