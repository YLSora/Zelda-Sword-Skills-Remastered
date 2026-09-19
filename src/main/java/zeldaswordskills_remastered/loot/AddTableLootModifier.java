package zeldaswordskills_remastered.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import org.jetbrains.annotations.NotNull;
import zeldaswordskills_remastered.config.ZSSConfig;

public final class AddTableLootModifier extends LootModifier {
    public static final Codec<AddTableLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance)
            .and(ResourceLocation.CODEC.fieldOf("loot_table").forGetter(modifier -> modifier.lootTable))
            .apply(instance, AddTableLootModifier::new));

    private final ResourceLocation lootTable;

    public AddTableLootModifier(LootItemCondition[] conditions, ResourceLocation lootTable) {
        super(conditions);
        this.lootTable = lootTable;
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!ZSSConfig.SERVER.generateZssLoot.get()) return generatedLoot;
        context.getResolver().getLootTable(lootTable).getRandomItemsRaw(context, generatedLoot::add);
        return generatedLoot;
    }

    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
