package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Stable dungeon identities shared by keys, doors, world generation, and encounter state. */
public enum DungeonType implements StringRepresentable {
    DESERT("desert", "desert_boss"),
    EARTH("mountain", "darknut_boss"),
    FIRE("hell", "fire_boss"),
    FOREST("forest", "forest_boss"),
    ICE("taiga", "ice_boss"),
    WATER("ocean", "water_boss"),
    END("end", "wizzrobe_grand");

    private static final Map<ResourceLocation, DungeonType> BY_ID = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(DungeonType::id, Function.identity()));

    private final ResourceLocation id;
    private final TagKey<Biome> biomeTag;
    private final ResourceLocation bossEntityId;

    DungeonType(String idPath, String bossEntityPath) {
        id = modId(idPath);
        biomeTag = TagKey.create(Registries.BIOME, modId("dungeons/" + idPath));
        bossEntityId = modId(bossEntityPath);
    }

    public ResourceLocation id() {
        return id;
    }

    public TagKey<Biome> biomeTag() {
        return biomeTag;
    }

    public ResourceLocation bossEntityId() {
        return bossEntityId;
    }

    public static Optional<DungeonType> byId(ResourceLocation id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    private static ResourceLocation modId(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }
}
