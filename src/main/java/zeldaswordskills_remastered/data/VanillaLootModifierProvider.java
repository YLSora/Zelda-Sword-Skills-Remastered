package zeldaswordskills_remastered.data;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.data.GlobalLootModifierProvider;
import net.minecraftforge.common.loot.LootTableIdCondition;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.loot.AddTableLootModifier;

import java.util.LinkedHashMap;
import java.util.Map;

public final class VanillaLootModifierProvider extends GlobalLootModifierProvider {
    public VanillaLootModifierProvider(PackOutput output) { super(output, ZeldaSwordSkills_Remastered.MOD_ID); }

    @Override
    protected void start() {
        Map<ResourceLocation, ResourceLocation> targets = new LinkedHashMap<>();
        ResourceLocation common = DungeonLootTables.id("chests/inject/vanilla_common");
        targets.put(BuiltInLootTables.ABANDONED_MINESHAFT, common);
        targets.put(BuiltInLootTables.DESERT_PYRAMID, common);
        targets.put(BuiltInLootTables.JUNGLE_TEMPLE, common);
        targets.put(BuiltInLootTables.STRONGHOLD_CORRIDOR, common);
        targets.put(BuiltInLootTables.STRONGHOLD_CROSSING, common);
        targets.put(BuiltInLootTables.SIMPLE_DUNGEON, common);
        targets.put(BuiltInLootTables.STRONGHOLD_LIBRARY, DungeonLootTables.id("chests/inject/stronghold_library"));
        targets.put(BuiltInLootTables.VILLAGE_WEAPONSMITH, DungeonLootTables.id("chests/inject/village_blacksmith"));
        targets.put(BuiltInLootTables.SPAWN_BONUS_CHEST, DungeonLootTables.id("chests/inject/bonus_chest"));
        for (String grass : new String[]{"grass", "tall_grass"}) {
            targets.put(ResourceLocation.fromNamespaceAndPath("minecraft", "blocks/" + grass),
                    DungeonLootTables.id("blocks/inject/" + grass + "_heart"));
        }
        targets.forEach((target, addition) -> add(target.getPath().replace('/', '_'), new AddTableLootModifier(
                new LootItemCondition[]{LootTableIdCondition.builder(target).build()}, addition)));
    }
}
