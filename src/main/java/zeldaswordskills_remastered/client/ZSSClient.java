package zeldaswordskills_remastered.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.client.particle.CycloneParticle;
import zeldaswordskills_remastered.client.model.ZSSLegacyModels;
import zeldaswordskills_remastered.client.model.ZSSModelLayers;
import zeldaswordskills_remastered.client.screen.PedestalScreen;
import zeldaswordskills_remastered.client.PedestalRenderer;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.worldgen.DungeonType;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZSSClient {
    private ZSSClient() {
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("combat", ZSSCombatOverlay::render);
        ZeldaSwordSkills_Remastered.LOGGER.info("ZeldaSwordSkills_Remastered client setup complete");
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ZSSKeyMappings.TARGET);
        event.register(ZSSKeyMappings.CLEAR_TARGET);
        event.register(ZSSKeyMappings.SKILL_BOOK);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // The guard's arm pose is a custom enum constant, so it has to exist before anything is
            // rendered: the model builds its pose switch from whichever constants are registered.
            ZSSParryAnimation.bootstrap();
            MenuScreens.register(ZSSRegistries.PEDESTAL_MENU.get(), PedestalScreen::new);
            for (String shield : java.util.List.of("deku_shield", "hylian_shield", "mirror_shield")) {
                ItemProperties.register(ZSSRegistries.getItem(shield),
                        ResourceLocation.withDefaultNamespace("blocking"), (stack, level, living, seed) ->
                                living != null && living.isUsingItem() && living.getUseItem() == stack ? 1.0F : 0.0F);
            }
            ItemProperties.register(ZSSRegistries.getItem("magic_mirror"),
                    ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "using"), (stack, level, living, seed) -> {
                        if (living == null || !living.isUsingItem() || living.getUseItem() != stack) return 0.0F;
                        return Math.min(1.0F, (stack.getUseDuration() - living.getUseItemRemainingTicks()) / 140.0F);
                    });
            ItemProperties.register(ZSSRegistries.getItem("skill_orb"),
                    ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "skill"), (stack, level, living, seed) -> {
                        ResourceLocation skill = ResourceLocation.tryParse(stack.getOrCreateTag().getString(ProgressionItem.SKILL_TAG));
                        int index = ZSSContentIds.SKILL_ORDER.indexOf(skill);
                        return index < 0 ? 1.0F : index + 1.0F;
                    });
            ItemProperties.register(ZSSRegistries.getItem("big_key"),
                    ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "dungeon"), (stack, level, living, seed) -> {
                        var dungeon = zeldaswordskills_remastered.item.BigKeyItem.dungeon(stack);
                        for (int i = 0; i < DungeonType.values().length; i++)
                            if (dungeon.orElse(null) == DungeonType.values()[i]) return i + 1.0F;
                        return 0.0F;
                    });
            ItemProperties.register(ZSSRegistries.getItem("hero_bow"),
                    ResourceLocation.withDefaultNamespace("pulling"), (stack, level, living, seed) ->
                            living != null && living.isUsingItem() && living.getUseItem() == stack ? 1.0F : 0.0F);
            ItemProperties.register(ZSSRegistries.getItem("hero_bow"),
                    ResourceLocation.withDefaultNamespace("pull"), (stack, level, living, seed) -> {
                        if (living == null || !living.isUsingItem() || living.getUseItem() != stack) return 0.0F;
                        return (stack.getUseDuration() - living.getUseItemRemainingTicks()) / 20.0F;
                    });
            ItemBlockRenderTypes.setRenderLayer(ZSSRegistries.BOMB_FLOWER.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ZSSRegistries.CERAMIC_JAR.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ZSSRegistries.CHEST_INVISIBLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ZSSRegistries.CHEST_LOCKED.get(), RenderType.cutout());
            ZSSRegistries.BOSS_DOORS.forEach(block -> ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout()));
            ItemBlockRenderTypes.setRenderLayer(ZSSRegistries.DOOR_LOCKED.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ZSSRegistries.INSCRIPTION.get(), RenderType.cutout());
            ZSSRegistries.SACRED_FLAMES.forEach(block -> ItemBlockRenderTypes.setRenderLayer(block.get(), RenderType.cutout()));
        });
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ZSSRegistries.PEDESTAL_BLOCK_ENTITY.get(), PedestalRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.ROCK.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.BOMB.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.BOOMERANG.get(), ToolProjectileRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.SEEDSHOT.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.MAGIC_SPELL.get(), MagicSpellRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.CYCLONE_PROJECTILE.get(), MagicSpellRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.HOOKSHOT.get(), ToolProjectileRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.WHIP.get(), ToolProjectileRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.ARROW_CUSTOM.get(), ZeldaArrowRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.ARROW_BOMB.get(), ZeldaArrowRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.ARROW_ELEMENTAL.get(), ZeldaArrowRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.SWORD_BEAM.get(), SwordBeamRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.GORON.get(), QuestNpcRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.BARNES.get(), QuestNpcRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.MASK_TRADER.get(), QuestNpcRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.ORCA.get(), QuestNpcRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.ZELDA.get(), QuestNpcRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.DARKNUT.get(), DarknutRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.DARKNUT_MIGHTY.get(), DarknutRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.FAIRY.get(), FairyRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.NAVI.get(), FairyRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.CHU.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.CHU_GREEN.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.CHU_BLUE.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.CHU_YELLOW.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.BABA_DEKU.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.BABA_FIRE.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.BABA_WITHERED.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.KEESE.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.KEESE_FIRE.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.KEESE_ICE.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.KEESE_THUNDER.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.KEESE_CURSED.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.OCTOROK.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.OCTOROK_BOMB.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.SKULLTULA.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.SKULLTULA_GOLD.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.WIZZROBE.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.WIZZROBE_ICE.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.WIZZROBE_LIGHTNING.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.WIZZROBE_WIND.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.DARKNUT_BOSS.get(), DarknutRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.DESERT_BOSS.get(), net.minecraft.client.renderer.entity.HuskRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.FIRE_BOSS.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.FOREST_BOSS.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.ICE_BOSS.get(), net.minecraft.client.renderer.entity.IllusionerRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.WATER_BOSS.get(), LegacyCreatureRenderer::new);
        event.registerEntityRenderer(ZSSRegistries.WIZZROBE_GRAND.get(), LegacyCreatureRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ZSSModelLayers.DARKNUT, ZSSLegacyModels::humanoidArmor);
        event.registerLayerDefinition(ZSSModelLayers.GORON, ZSSLegacyModels::goron);
        event.registerLayerDefinition(ZSSModelLayers.MASK_TRADER, ZSSLegacyModels::maskTrader);
        event.registerLayerDefinition(ZSSModelLayers.OCTOROK, ZSSLegacyModels::octorok);
        event.registerLayerDefinition(ZSSModelLayers.WIZZROBE, ZSSLegacyModels::wizzrobe);
        event.registerLayerDefinition(ZSSModelLayers.BABA_DEKU, () -> ZSSLegacyModels.dekuBaba(false, false));
        event.registerLayerDefinition(ZSSModelLayers.BABA_FIRE, () -> ZSSLegacyModels.dekuBaba(true, false));
        event.registerLayerDefinition(ZSSModelLayers.BABA_WITHERED, () -> ZSSLegacyModels.dekuBaba(false, true));
        event.registerLayerDefinition(ZSSModelLayers.KEESE, ZSSLegacyModels::keese);
        event.registerLayerDefinition(ZSSModelLayers.HOOKSHOT_HEAD, zeldaswordskills_remastered.client.model.HookshotHeadModel::createLayer);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ZSSRegistries.CYCLONE.get(), CycloneParticle.Provider::new);
    }
}
