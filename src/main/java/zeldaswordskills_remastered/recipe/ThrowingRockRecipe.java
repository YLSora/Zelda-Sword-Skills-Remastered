package zeldaswordskills_remastered.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class ThrowingRockRecipe extends CustomRecipe {
    private static final int ROCKS_PER_COBBLESTONE = 4;

    public ThrowingRockRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return isCobblestoneToRocks(container) || isRocksToCobblestone(container);
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        if (isCobblestoneToRocks(container)) {
            return new ItemStack(ZSSRegistries.THROWING_ROCK.get(), ROCKS_PER_COBBLESTONE);
        }
        if (isRocksToCobblestone(container)) {
            return new ItemStack(Items.COBBLESTONE);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ZSSRegistries.THROWING_ROCK_RECIPE.get();
    }

    private boolean isCobblestoneToRocks(CraftingContainer container) {
        int cobblestone = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(Items.COBBLESTONE) || stack.getCount() != 1) {
                return false;
            }
            cobblestone++;
        }
        return cobblestone == 1;
    }

    private boolean isRocksToCobblestone(CraftingContainer container) {
        int rocks = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (!stack.is(ZSSRegistries.THROWING_ROCK.get())) {
                return false;
            }
            rocks += stack.getCount();
        }
        return rocks == ROCKS_PER_COBBLESTONE;
    }
}
