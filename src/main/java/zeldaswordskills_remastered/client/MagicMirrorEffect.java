package zeldaswordskills_remastered.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.item.SpecialItems;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.MagicMirrorService;

import java.io.IOException;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class MagicMirrorEffect {
    private static ClientLevel source;
    private static long started;
    private static SoundInstance sound;
    private static PostChain shader;
    private static PostPass warp;
    private static boolean shaderFailed;
    private static int width;
    private static int height;

    private MagicMirrorEffect() {}

    public static void apply(boolean active) {
        reset();
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.level == null || minecraft.player == null) return;
        source = minecraft.level;
        started = source.getGameTime();
        minecraft.player.stopUsingItem();
        // Consume the held click: another warp requires a new right-click after completion.
        minecraft.options.keyUse.setDown(false);
        while (minecraft.options.keyUse.consumeClick()) {}
        sound = SimpleSoundInstance.forLocalAmbience(ZSSRegistries.WORLD_WARP.get(), 1.0F, 1.0F);
        minecraft.getSoundManager().play(sound);
    }

    private static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        if (source != null && (minecraft.level != source || minecraft.player == null || !minecraft.player.isAlive())) reset();
        return source != null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) active();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        reset();
    }

    @SubscribeEvent
    public static void renderCountdown(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || active() || !minecraft.player.isUsingItem()
                || !(minecraft.player.getUseItem().getItem() instanceof SpecialItems.MagicMirror)) return;
        int seconds = Mth.clamp((minecraft.player.getUseItemRemainingTicks() + 19) / 20, 1, 3);
        var graphics = event.getGuiGraphics();
        // Match the vanilla title subtitle's anchor and two-times text scale.
        graphics.pose().pushPose();
        graphics.pose().translate(event.getWindow().getGuiScaledWidth() / 2.0F,
                event.getWindow().getGuiScaledHeight() / 2.0F + 15, 0);
        graphics.pose().scale(2, 2, 2);
        graphics.drawCenteredString(minecraft.font, "-- " + seconds + " --", 0, 0, 0xFFFFFF);
        graphics.pose().popPose();
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        if (active()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void renderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || !active() || shaderFailed) return;
        Minecraft minecraft = Minecraft.getInstance();
        var target = minecraft.getMainRenderTarget();
        if (shader == null) {
            try {
                shader = new PostChain(minecraft.getTextureManager(), minecraft.getResourceManager(), target,
                        ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "shaders/post/magic_mirror.json"));
                warp = shader.addPass(ZeldaSwordSkills_Remastered.MOD_ID + ":magic_mirror", target, shader.getTempTarget("swap"));
                shader.addPass("blit", shader.getTempTarget("swap"), target);
            } catch (IOException | RuntimeException exception) {
                releaseRenderer();
                shaderFailed = true;
                ZeldaSwordSkills_Remastered.LOGGER.error("Unable to load Magic Mirror shader", exception);
                return;
            }
        }
        if (width != target.width || height != target.height) {
            width = target.width;
            height = target.height;
            shader.resize(width, height);
        }
        float elapsed = source.getGameTime() - started + event.getPartialTick();
        warp.getEffect().safeGetUniform("Progress").set(Mth.clamp(elapsed / MagicMirrorService.SOUND_TICKS, 0, 1));
        warp.getEffect().safeGetUniform("WarpTime").set(elapsed / 20.0F);
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            shader.process(event.getPartialTick());
        } finally {
            target.bindWrite(true);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    public static void releaseRenderer() {
        if (shader != null) shader.close();
        shader = null;
        warp = null;
        width = height = 0;
        shaderFailed = false;
    }

    private static void reset() {
        source = null;
        if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
        sound = null;
        releaseRenderer();
    }
}
