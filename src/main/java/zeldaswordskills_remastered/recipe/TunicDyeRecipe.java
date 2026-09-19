package zeldaswordskills_remastered.recipe;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class TunicDyeRecipe extends CustomRecipe {
    public TunicDyeRecipe(ResourceLocation id, CraftingBookCategory category) { super(id, category); }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        Conversion conversion = conversion();
        int leggings = 0;
        int dyes = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) continue;
            if (stack.is(ZSSRegistries.getItem(conversion.source()))) leggings++;
            else if (stack.is(conversion.dye())) dyes++;
            else return false;
        }
        return leggings == 1 && dyes == 1;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        if (!matches(container, null)) return ItemStack.EMPTY;
        ItemStack source = ItemStack.EMPTY;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack candidate = container.getItem(slot);
            if (candidate.is(ZSSRegistries.getItem(conversion().source()))) { source = candidate; break; }
        }
        ItemStack result = new ItemStack(ZSSRegistries.getItem(conversion().result()));
        if (source.hasTag()) result.setTag(source.getTag().copy());
        if (result.isDamageableItem()) result.setDamageValue(Math.min(result.getDamageValue(), result.getMaxDamage() - 1));
        return result;
    }

    @Override public boolean canCraftInDimensions(int width, int height) { return width * height >= 2; }
    @Override public RecipeSerializer<?> getSerializer() { return ZSSRegistries.TUNIC_DYE_RECIPE.get(); }

    private Conversion conversion() {
        return switch (getId().getPath()) {
            case "hero_from_goron" -> new Conversion("goron_tunic_leggings", Items.GREEN_DYE, "hero_tunic_leggings");
            case "hero_from_zora" -> new Conversion("zora_tunic_leggings", Items.GREEN_DYE, "hero_tunic_leggings");
            case "goron_from_hero" -> new Conversion("hero_tunic_leggings", Items.RED_DYE, "goron_tunic_leggings");
            case "goron_from_zora" -> new Conversion("zora_tunic_leggings", Items.RED_DYE, "goron_tunic_leggings");
            case "zora_from_hero" -> new Conversion("hero_tunic_leggings", Items.BLUE_DYE, "zora_tunic_leggings");
            case "zora_from_goron" -> new Conversion("goron_tunic_leggings", Items.BLUE_DYE, "zora_tunic_leggings");
            default -> throw new IllegalStateException("Unknown tunic dye recipe: " + getId());
        };
    }

    private record Conversion(String source, Item dye, String result) { }
}
