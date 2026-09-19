package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.registries.RegistryObject;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.List;

/** Natural-spawn policy. Explicit dungeon spawning intentionally bypasses these habitat checks. */
public final class CreatureSpawnRules {
    public static final TagKey<Biome> HOT = biomeTag("hot");
    public static final TagKey<Biome> COLD = biomeTag("cold");
    public static final TagKey<Biome> WET = biomeTag("wet");
    public static final TagKey<Biome> DRY = biomeTag("dry");
    public static final TagKey<Biome> GENERAL = biomeTag("general");
    public static final TagKey<Biome> WATER_RICH = biomeTag("water_rich");
    public static final TagKey<Biome> DEKU_BABA_HABITAT = biomeTag("deku_baba_habitat");

    /** Stable data-pack boundary for the later dungeon encounter system. */
    public static final TagKey<EntityType<?>> DUNGEON_ONLY = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "dungeon_only"));
    private static final ResourceKey<Structure> FOREST_TEMPLE = ResourceKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "forest_dungeon"));

    public static final int DEEP_CAVE_MAX_Y = 0;
    public static final int GOLD_SKULLTULA_CHANCE = 100;
    public static final int MIGHTY_DARKNUT_CHANCE = 100;
    public static final int FAIRY_CHANCE = 5000;

    private static final List<RegistryObject<EntityType<LegacyCreature>>> NATURAL_TYPES = List.of(
            ZSSRegistries.DARKNUT, ZSSRegistries.DARKNUT_MIGHTY, ZSSRegistries.FAIRY,
            ZSSRegistries.CHU, ZSSRegistries.CHU_GREEN, ZSSRegistries.CHU_BLUE, ZSSRegistries.CHU_YELLOW,
            ZSSRegistries.BABA_DEKU, ZSSRegistries.BABA_FIRE, ZSSRegistries.BABA_WITHERED,
            ZSSRegistries.KEESE, ZSSRegistries.KEESE_FIRE, ZSSRegistries.KEESE_ICE,
            ZSSRegistries.KEESE_THUNDER, ZSSRegistries.KEESE_CURSED,
            ZSSRegistries.OCTOROK, ZSSRegistries.OCTOROK_BOMB,
            ZSSRegistries.SKULLTULA, ZSSRegistries.SKULLTULA_GOLD,
            ZSSRegistries.WIZZROBE, ZSSRegistries.WIZZROBE_ICE,
            ZSSRegistries.WIZZROBE_LIGHTNING, ZSSRegistries.WIZZROBE_WIND);

    private CreatureSpawnRules() {}

    public static void registerPlacements() {
        NATURAL_TYPES.forEach(type -> SpawnPlacements.register(type.get(), placement(type),
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, CreatureSpawnRules::canSpawnCreature));
    }

    private static SpawnPlacements.Type placement(RegistryObject<EntityType<LegacyCreature>> type) {
        if (type == ZSSRegistries.OCTOROK) return SpawnPlacements.Type.IN_WATER;
        return SpawnPlacements.Type.ON_GROUND;
    }

    private static boolean canSpawnCreature(EntityType<? extends Mob> type, LevelAccessor level, MobSpawnType reason,
                                            BlockPos pos, RandomSource random) {
        if (!isNaturalSpawn(reason)) return Mob.checkMobSpawnRules(type, level, reason, pos, random);
        if (!(level instanceof ServerLevel server) || !matchesNaturalHabitat(type, server, pos)) return false;
        if (!server.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return false;
        if (type.getCategory() == MobCategory.MONSTER && !naturalMonstersAllowed(server)) return false;
        if (type.getCategory() == MobCategory.MONSTER && server.isDay()) return false;
        if (type == ZSSRegistries.SKULLTULA_GOLD.get() && random.nextInt(GOLD_SKULLTULA_CHANCE) != 0) return false;
        if (type == ZSSRegistries.DARKNUT_MIGHTY.get() && random.nextInt(MIGHTY_DARKNUT_CHANCE) != 0) return false;
        if (type == ZSSRegistries.FAIRY.get() && random.nextInt(FAIRY_CHANCE) != 0) return false;
        return Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    public static boolean naturalMonstersAllowed(ServerLevel level) {
        return ZSSConfig.SERVER.naturalMonsterSpawning.get()
                && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    /** Returns whether a position belongs to a generated Forest Temple structure piece. */
    public static boolean insideForestTemple(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, FOREST_TEMPLE);
        return start != null && start.isValid();
    }

    public static boolean matchesNaturalHabitat(EntityType<?> type, ServerLevel level, BlockPos pos) {
        ResourceKey<Level> dimension = level.dimension();
        if (!matchesBiome(type, dimension, level.getBiome(pos))) return false;
        if (dimension != Level.OVERWORLD) return true;
        if (pos.getY() <= DEEP_CAVE_MAX_Y) return isDarknut(type) || isSkulltula(type);
        return !isSkulltula(type);
    }

    public static boolean matchesBiome(EntityType<?> type, ResourceKey<Level> dimension, Holder<Biome> biome) {
        if (dimension == Level.NETHER) {
            if (type == ZSSRegistries.OCTOROK_BOMB.get()) return biome.is(net.minecraft.world.level.biome.Biomes.BASALT_DELTAS);
            if (type == ZSSRegistries.BABA_FIRE.get()) return biome.is(net.minecraft.world.level.biome.Biomes.CRIMSON_FOREST)
                    || biome.is(net.minecraft.world.level.biome.Biomes.WARPED_FOREST);
            if (type == ZSSRegistries.KEESE_CURSED.get()) return biome.is(net.minecraft.world.level.biome.Biomes.SOUL_SAND_VALLEY);
            if (type == ZSSRegistries.CHU.get() || isDarknut(type)) return true;
            if (type == ZSSRegistries.WIZZROBE.get()) return biome.is(net.minecraft.world.level.biome.Biomes.NETHER_WASTES)
                    || biome.is(net.minecraft.world.level.biome.Biomes.CRIMSON_FOREST);
            if (type == ZSSRegistries.WIZZROBE_ICE.get()) return biome.is(net.minecraft.world.level.biome.Biomes.WARPED_FOREST);
            if (type == ZSSRegistries.WIZZROBE_LIGHTNING.get()) return biome.is(net.minecraft.world.level.biome.Biomes.SOUL_SAND_VALLEY);
            if (type == ZSSRegistries.WIZZROBE_WIND.get()) return biome.is(net.minecraft.world.level.biome.Biomes.BASALT_DELTAS);
            return false;
        }
        return dimension == Level.OVERWORLD && matchesOverworldBiome(type, biome);
    }

    public static boolean matchesOverworldBiome(EntityType<?> type, Holder<Biome> biome) {
        if (biome.is(net.minecraft.world.level.biome.Biomes.DEEP_DARK)) return false;
        if (isDarknut(type) || isSkulltula(type)) return true;
        if (type == ZSSRegistries.CHU.get() || type == ZSSRegistries.KEESE_FIRE.get()) return biome.is(HOT);
        if (type == ZSSRegistries.CHU_BLUE.get() || type == ZSSRegistries.KEESE_ICE.get()) return biome.is(COLD);
        if (type == ZSSRegistries.CHU_YELLOW.get() || type == ZSSRegistries.KEESE_THUNDER.get()) return biome.is(WET);
        if (type == ZSSRegistries.CHU_GREEN.get() || type == ZSSRegistries.KEESE.get()) return biome.is(GENERAL);
        if (type == ZSSRegistries.BABA_DEKU.get()) return biome.is(DEKU_BABA_HABITAT);
        if (type == ZSSRegistries.BABA_WITHERED.get()) return biome.is(DRY);
        if (type == ZSSRegistries.OCTOROK.get()) return biome.is(WATER_RICH);
        if (type == ZSSRegistries.FAIRY.get()) return true;
        return false;
    }

    private static boolean isDarknut(EntityType<?> type) {
        return type == ZSSRegistries.DARKNUT.get() || type == ZSSRegistries.DARKNUT_MIGHTY.get();
    }

    private static boolean isSkulltula(EntityType<?> type) {
        return type == ZSSRegistries.SKULLTULA.get() || type == ZSSRegistries.SKULLTULA_GOLD.get();
    }

    private static boolean isNaturalSpawn(MobSpawnType reason) {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION
                || reason == MobSpawnType.REINFORCEMENT;
    }

    private static TagKey<Biome> biomeTag(String path) {
        return TagKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path));
    }
}
