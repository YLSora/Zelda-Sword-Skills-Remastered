package zeldaswordskills_remastered.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import zeldaswordskills_remastered.worldgen.DungeonType;

public final class BigKeyItem extends Item {
    public static final String DUNGEON_TAG = "dungeon";
    public BigKeyItem(Properties properties) { super(properties); }

    public static ItemStack forDungeon(Item item, ResourceLocation dungeon) {
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(DUNGEON_TAG, dungeon.toString());
        return stack;
    }

    public static Optional<DungeonType> dungeon(ItemStack stack) {
        if (stack.getTag() == null) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(stack.getTag().getString(DUNGEON_TAG));
        return id == null ? Optional.empty() : DungeonType.byId(id);
    }

    @Override public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(dungeon(stack).map(type -> Component.translatable("tooltip.zeldaswordskills_remastered.big_key.dungeon",
                        Component.translatable("block.zeldaswordskills_remastered.door_boss_" + type.getSerializedName()))
                .withStyle(ChatFormatting.GOLD)).orElseGet(() ->
                        Component.translatable("tooltip.zeldaswordskills_remastered.big_key.unbound").withStyle(ChatFormatting.RED)));
    }
}
