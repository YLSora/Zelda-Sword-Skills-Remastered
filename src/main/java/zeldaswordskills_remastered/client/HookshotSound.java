package zeldaswordskills_remastered.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** A loop per tracked hook, stopped with that entity rather than a global sound ID. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class HookshotSound extends AbstractTickableSoundInstance {
    private final ToolProjectile hook;

    private HookshotSound(ToolProjectile hook) {
        super(ZSSRegistries.HOOKSHOT_SOUND.get(), SoundSource.PLAYERS, RandomSource.create());
        this.hook = hook;
        looping = true;
        delay = 0;
        volume = 0.8F;
        updatePosition();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof ToolProjectile hook
                && hook.mode() == ToolProjectile.Mode.HOOKSHOT) {
            Minecraft.getInstance().getSoundManager().play(new HookshotSound(hook));
        }
    }

    @Override public void tick() {
        if (hook.isRemoved() || Minecraft.getInstance().level != hook.level()) {
            stop();
            return;
        }
        updatePosition();
    }

    private void updatePosition() {
        var position = hook.getOwner() == null ? hook.position()
                : hook.position().add(hook.getOwner().getEyePosition()).scale(0.5D);
        x = position.x;
        y = position.y;
        z = position.z;
    }
}
