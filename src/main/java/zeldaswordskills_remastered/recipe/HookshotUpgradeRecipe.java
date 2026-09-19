package zeldaswordskills_remastered.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.item.StageNineToolItem;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class HookshotUpgradeRecipe extends CustomRecipe {
    public HookshotUpgradeRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }

    @Override public boolean matches(CraftingContainer container, Level level) {
        String upgrade = upgradePath();
        ItemStack hook = ItemStack.EMPTY;
        int materials = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            if (StageNineToolItem.isHook(stack) && hook.isEmpty()) hook = stack;
            else if (stack.is(ZSSRegistries.getItem(upgrade))) materials++;
            else return false;
        }
        return !hook.isEmpty() && materials == 1 && !StageNineToolItem.upgradeHook(hook, upgrade).isEmpty();
    }

    @Override public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        if (!matches(container, null)) return ItemStack.EMPTY;
        for (int slot = 0; slot < container.getContainerSize(); slot++) if (StageNineToolItem.isHook(container.getItem(slot))) {
            return StageNineToolItem.upgradeHook(container.getItem(slot), upgradePath());
        }
        return ItemStack.EMPTY;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ZSSRegistries.HOOKSHOT_UPGRADE_RECIPE.get(); }

    private String upgradePath() {
        return switch (getId().getPath()) {
            case "hookshot_claw_upgrade" -> "claw_upgrade";
            case "hookshot_multi_upgrade" -> "multi_hook_upgrade";
            default -> "hookshot_extender";
        };
    }
}
