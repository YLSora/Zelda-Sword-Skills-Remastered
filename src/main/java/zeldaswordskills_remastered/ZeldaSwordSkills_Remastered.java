package zeldaswordskills_remastered;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import zeldaswordskills_remastered.data.ZSSDataGenerators;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.entity.LegacyCreature;
import zeldaswordskills_remastered.entity.CreatureSpawnRules;
import zeldaswordskills_remastered.entity.ZssNaturalSpawner;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraft.world.entity.EntityType;
import zeldaswordskills_remastered.entity.npc.QuestNpc;

@Mod(ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZeldaSwordSkills_Remastered {
    public static final String MOD_ID = "zeldaswordskills_remastered";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ZeldaSwordSkills_Remastered(FMLJavaModLoadingContext loadingContext) {
        IEventBus modBus = loadingContext.getModEventBus();
        ZSSRegistries.register(modBus);
        loadingContext.registerConfig(ModConfig.Type.SERVER, ZSSConfig.SERVER_SPEC);
        loadingContext.registerConfig(ModConfig.Type.CLIENT, ZSSConfig.CLIENT_SPEC);
        modBus.addListener(ZSSCapabilities::register);
        modBus.addListener(this::modifyEntityAttributes);
        modBus.addListener(this::createEntityAttributes);
        modBus.addListener(ZSSDataGenerators::gatherData);
        modBus.addListener(this::commonSetup);
        ZssNaturalSpawner.register();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ZSSNetwork.initialize();
            CreatureSpawnRules.registerPlacements();
        });
        LOGGER.info("ZeldaSwordSkills_Remastered common setup complete");
    }

    private void modifyEntityAttributes(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, ZSSRegistries.MAX_MAGIC.get());
    }

    private void createEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ZSSRegistries.ICE_BOSS.get(), zeldaswordskills_remastered.entity.IceBossCreature.createAttributes().build());
        event.put(ZSSRegistries.DESERT_BOSS.get(), zeldaswordskills_remastered.entity.DesertBossCreature.createAttributes().build());
        var attributes = QuestNpc.createMobAttributes().build();
        event.put(ZSSRegistries.GORON.get(), attributes);
        event.put(ZSSRegistries.BARNES.get(), attributes);
        event.put(ZSSRegistries.MASK_TRADER.get(), attributes);
        event.put(ZSSRegistries.ORCA.get(), attributes);
        event.put(ZSSRegistries.ZELDA.get(), attributes);
        ZSSRegistries.LEGACY_CREATURE_TYPES.forEach(type ->
                event.put(type.get(), LegacyCreature.createAttributes().build()));
    }
}
