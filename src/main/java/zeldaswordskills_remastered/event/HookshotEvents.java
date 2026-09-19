package zeldaswordskills_remastered.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;

/** Release temporary pulls before players leave or the server saves and closes. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class HookshotEvents {
    private HookshotEvents() { }

    @SubscribeEvent
    public static void ownerLeft(EntityLeaveLevelEvent event) {
        if ((event.getEntity() instanceof LivingEntity || event.getEntity() instanceof ItemEntity)
                && event.getLevel() instanceof ServerLevel level) {
            hooks(level).stream().filter(hook -> hook.referencesHookEntity(event.getEntity())).forEach(ToolProjectile::discard);
        }
    }

    @SubscribeEvent
    public static void loggingOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level)
            hooks(level).stream().filter(hook -> hook.referencesHookEntity(event.getEntity())).forEach(ToolProjectile::discard);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            hooks(level).forEach(ToolProjectile::discard);
        }
    }

    private static java.util.List<ToolProjectile> hooks(ServerLevel level) {
        return java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(), false)
                .filter(entity -> entity instanceof ToolProjectile hook && hook.mode().isTethered())
                .map(entity -> (ToolProjectile) entity).toList();
    }
}
