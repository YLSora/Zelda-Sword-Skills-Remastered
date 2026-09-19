package zeldaswordskills_remastered.entity;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;

/** Creates the data-driven damage sources used by Zelda projectiles and creatures. */
public final class ZSSDamageSources {
    public static final ResourceKey<DamageType> FIRE = key("fire");
    public static final ResourceKey<DamageType> ICE = key("ice");
    public static final ResourceKey<DamageType> LIGHTNING = key("lightning");
    public static final ResourceKey<DamageType> WIND = key("wind");
    public static final ResourceKey<DamageType> HOLY = key("holy");
    public static final ResourceKey<DamageType> MAGIC = key("magic");
    public static final ResourceKey<DamageType> QUAKE = key("quake");

    private ZSSDamageSources() {
    }

    public static DamageSource projectile(Level level, ElementalDamage.Element element, Entity direct, Entity owner) {
        if (owner instanceof LegacyCreature) return level.damageSources().thrown(direct, owner);
        return source(level, keyFor(element), direct, owner);
    }

    public static DamageSource toolProjectile(Level level, ToolProjectile.Mode mode, Entity direct, Entity owner) {
        ElementalDamage.Element element = switch (mode) {
            case FIRE -> ElementalDamage.Element.FIRE;
            case ICE -> ElementalDamage.Element.ICE;
            case LIGHTNING -> ElementalDamage.Element.LIGHTNING;
            case WIND -> ElementalDamage.Element.WIND;
            default -> ElementalDamage.Element.NONE;
        };
        return element == ElementalDamage.Element.NONE
                ? level.damageSources().thrown(direct, owner)
                : projectile(level, element, direct, owner);
    }

    private static DamageSource source(Level level, ResourceKey<DamageType> key, Entity direct, Entity owner) {
        Holder<DamageType> holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
        return new DamageSource(holder, direct, owner);
    }

    private static ResourceKey<DamageType> keyFor(ElementalDamage.Element element) {
        return switch (element) {
            case FIRE -> FIRE;
            case ICE -> ICE;
            case LIGHTNING -> LIGHTNING;
            case WIND -> WIND;
            case LIGHT -> HOLY;
            case MAGIC -> MAGIC;
            case QUAKE -> QUAKE;
            case NONE -> throw new IllegalArgumentException("No damage type exists for a neutral attack");
        };
    }

    private static ResourceKey<DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path));
    }
}
