package zeldaswordskills_remastered.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.item.BombBagItem;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class CombineBombBagRecipe extends CustomRecipe {
    public CombineBombBagRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    @Override public boolean matches(CraftingContainer container, Level level) {
        int found = 0; ItemStack first = ItemStack.EMPTY, second = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) if (!container.getItem(i).isEmpty()) {
            if (!container.getItem(i).is(ZSSRegistries.getItem("bomb_bag")) || ++found > 2) return false;
            if (first.isEmpty()) first = container.getItem(i); else second = container.getItem(i);
        }
        return found == 2 && compatible(first, second);
    }
    private static boolean compatible(ItemStack a, ItemStack b) {
        return BombBagItem.capacity(a) + BombBagItem.capacity(b) <= BombBagItem.MAX_CAPACITY;
    }
    @Override public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        ItemStack result = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) if (!container.getItem(i).isEmpty()) {
            if (result.isEmpty()) result = container.getItem(i).copy(); else { BombBagItem.combine(result, container.getItem(i)); break; }
        }
        return result;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ZSSRegistries.COMBINE_BOMB_BAG_RECIPE.get(); }
}
