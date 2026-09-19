package zeldaswordskills_remastered.event;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.CreatureSpawnRules;

/** Prevents vanilla hostile mobs from filling the generated Forest Temple rooms. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSSpawnEvents {
    private ZSSSpawnEvents() {
    }

    @SubscribeEvent
    public static void preventVanillaForestTempleMonsters(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (!isNaturalSpawn(event.getSpawnType()) || mob.getType().getCategory() != MobCategory.MONSTER
                || !"minecraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getNamespace())
                || !(event.getLevel() instanceof ServerLevel level)) return;
        if (CreatureSpawnRules.insideForestTemple(level, mob.blockPosition())) event.setSpawnCancelled(true);
    }

    private static boolean isNaturalSpawn(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.REINFORCEMENT;
    }
}
