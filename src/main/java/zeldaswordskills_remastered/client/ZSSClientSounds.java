package zeldaswordskills_remastered.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Applies local sound preferences, including pickups which never emit PlayLevelSoundEvent. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSClientSounds {
    private ZSSClientSounds() {
    }

    @SubscribeEvent
    public static void applyPreferences(PlaySoundEvent event) {
        SoundInstance original = event.getSound();
        if (original == null || !ZSSConfig.CLIENT_SPEC.isLoaded()) return;
        var location = original.getLocation();
        if (ZSSConfig.CLIENT.naviSilent.get() && (location.equals(ZSSRegistries.NAVI_VOICE.getId())
                || location.equals(ZSSRegistries.NAVI_FLOAT.getId())
                || location.equals(ZSSRegistries.NAVI_INTERACT.getId()))) {
            event.setSound(null);
            return;
        }
        if (!ZSSConfig.CLIENT.replacePickupSounds.get()) {
            if (location.equals(ZSSRegistries.GET_HEART.getId()) || location.equals(ZSSRegistries.GET_ITEM.getId()))
                event.setSound(replace(original, SoundEvents.ITEM_PICKUP.getLocation(), 0.2F, 1.0F));
            return;
        }
        if (!location.equals(SoundEvents.ITEM_PICKUP.getLocation())) return;

        boolean smallHeart = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            double x = original.getX();
            double y = original.getY();
            double z = original.getZ();
            smallHeart = !minecraft.level.getEntitiesOfClass(ItemEntity.class,
                            new net.minecraft.world.phys.AABB(x - 0.6D, y - 0.6D, z - 0.6D,
                                    x + 0.6D, y + 0.6D, z + 0.6D),
                            item -> item.getItem().is(ZSSRegistries.getItem("small_heart")))
                    .isEmpty();
        }
        event.setSound(replace(original,
                (smallHeart ? ZSSRegistries.GET_HEART.get() : ZSSRegistries.GET_ITEM.get()).getLocation(),
                0.2F, 1.0F));
    }

    private static SoundInstance replace(SoundInstance original, net.minecraft.resources.ResourceLocation sound,
                                         float volume, float pitch) {
        return new SimpleSoundInstance(sound, original.getSource(), volume, pitch, SoundInstance.createUnseededRandom(),
                original.isLooping(), original.getDelay(), original.getAttenuation(),
                original.getX(), original.getY(), original.getZ(), original.isRelative());
    }
}
