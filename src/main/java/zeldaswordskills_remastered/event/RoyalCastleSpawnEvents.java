package zeldaswordskills_remastered.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.worldgen.RoyalCastleWorldData;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class RoyalCastleSpawnEvents {
    private static final TagKey<EntityType<?>> BLOCKED = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "royal_castle_blocked_spawns"));

    private RoyalCastleSpawnEvents() { }

    public static boolean blocked(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER || mob instanceof Enemy
                || mob instanceof NeutralMob || mob.getType().is(BLOCKED);
    }

    public static boolean protectedPosition(ServerLevelAccessor level, BlockPos pos) {
        var site = RoyalCastleWorldData.get(level.getLevel());
        // Protect the full vertical column so caves and added floors remain peaceful.
        return site != null && site.contains(pos.getX(), pos.getZ());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void position(MobSpawnEvent.PositionCheck event) {
        if (blocked(event.getEntity()) && protectedPosition(event.getLevel(), event.getEntity().blockPosition())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (blocked(event.getEntity()) && protectedPosition(event.getLevel(), event.getEntity().blockPosition())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (!event.loadedFromDisk() && event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof Mob mob && blocked(mob)
                && protectedPosition(level, mob.blockPosition())) event.setCanceled(true);
    }
}
