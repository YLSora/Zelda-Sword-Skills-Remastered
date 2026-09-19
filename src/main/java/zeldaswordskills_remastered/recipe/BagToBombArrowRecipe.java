package zeldaswordskills_remastered.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class BagToBombArrowRecipe extends CustomRecipe {
    public BagToBombArrowRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }
    @Override public boolean matches(CraftingContainer container, Level level) {
        int arrows = 0, bags = 0;
        for (int i = 0; i < container.getContainerSize(); i++) { ItemStack s = container.getItem(i); if (s.isEmpty()) continue;
            if (s.is(Items.ARROW)) arrows++; else if (s.is(ZSSRegistries.getItem("bomb_bag"))) bags++; else return false; }
        if (arrows != 1 || bags != 1) return false;
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(ZSSRegistries.getItem("bomb_bag"))) {
            var tag = container.getItem(i).getOrCreateTagElement("bombs");
            return tag.getAllKeys().stream().anyMatch(k -> tag.getInt(k) > 0);
        }
        return false;
    }
    @Override public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        if (!matches(container, null)) return ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(ZSSRegistries.getItem("bomb_bag"))) {
            String type = zeldaswordskills_remastered.item.BombBagItem.selectedType(container.getItem(i));
            return new ItemStack(ZSSRegistries.getItem(switch (type) { case "fire_bomb" -> "fire_bomb_arrow"; case "water_bomb" -> "water_bomb_arrow"; default -> "bomb_arrow"; }));
        }
        return ItemStack.EMPTY;
    }
    @Override public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < container.getContainerSize(); i++) if (container.getItem(i).is(ZSSRegistries.getItem("bomb_bag"))) {
            ItemStack bag = container.getItem(i).copy();
            var counts = bag.getOrCreateTagElement("bombs");
            String type = zeldaswordskills_remastered.item.BombBagItem.selectedType(bag);
            counts.putInt(type, Math.max(0, counts.getInt(type) - 1));
            remaining.set(i, bag);
        }
        return remaining;
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ZSSRegistries.BAG_TO_BOMB_ARROW_RECIPE.get(); }
}
