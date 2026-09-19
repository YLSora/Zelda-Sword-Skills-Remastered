package zeldaswordskills_remastered.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.entity.projectile.ZeldaArrow;

public final class ElementalDamage {
    public enum Element { NONE, FIRE, ICE, LIGHTNING, WIND, LIGHT, MAGIC, QUAKE }
    private ElementalDamage() {}

    public static Element from(DamageSource source) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.MOB_ATTACK)
                && source.getDirectEntity() instanceof LegacyCreature creature) return switch (creature.kind()) {
            case CHU_RED, KEESE_FIRE, BABA_FIRE -> Element.FIRE;
            case CHU_BLUE, KEESE_ICE -> Element.ICE;
            case CHU_YELLOW, KEESE_THUNDER -> Element.LIGHTNING;
            case KEESE_CURSED -> Element.MAGIC;
            default -> Element.NONE;
        };
        var id = source.typeHolder().unwrapKey().filter(key -> key.location().getNamespace().equals(ZeldaSwordSkills_Remastered.MOD_ID))
                .map(key -> key.location().getPath()).orElse("");
        switch (id) {
            case "fire" -> { return Element.FIRE; }
            case "ice" -> { return Element.ICE; }
            case "lightning" -> { return Element.LIGHTNING; }
            case "wind" -> { return Element.WIND; }
            case "holy" -> { return Element.LIGHT; }
            case "magic" -> { return Element.MAGIC; }
            case "quake" -> { return Element.QUAKE; }
            default -> { }
        }
        if (source.getDirectEntity() instanceof ToolProjectile spell) return switch (spell.mode()) {
            case FIRE -> Element.FIRE; case ICE -> Element.ICE; case LIGHTNING -> Element.LIGHTNING;
            case WIND -> Element.WIND; default -> Element.NONE;
        };
        if (source.getDirectEntity() instanceof ZeldaArrow arrow) return switch (arrow.kind()) {
            case FIRE, FIRE_BOMB -> Element.FIRE; case ICE -> Element.ICE; case LIGHT -> Element.LIGHT;
            default -> Element.NONE;
        };
        if (source.getDirectEntity() instanceof ThrownBomb bomb && bomb.bombKind() == ThrownBomb.BombKind.FIRE)
            return Element.FIRE;
        return source.is(DamageTypeTags.IS_FIRE) ? Element.FIRE : Element.NONE;
    }
}
