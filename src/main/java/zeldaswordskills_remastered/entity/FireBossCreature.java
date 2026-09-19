package zeldaswordskills_remastered.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.worldgen.DungeonBossEquipment;

/** One of the four fixed elemental casters guarding the Fire Temple. */
public final class FireBossCreature extends WizzrobeCreature {
    private static final EntityDataAccessor<String> ELEMENT = SynchedEntityData.defineId(
            FireBossCreature.class, EntityDataSerializers.STRING);

    public FireBossCreature(EntityType<? extends LegacyCreature> type, Level level) {
        super(type, level, Kind.FIRE_BOSS);
    }

    @Override protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ELEMENT, "fire");
    }

    public void setElement(ToolProjectile.Mode element) {
        if (element != ToolProjectile.Mode.FIRE && element != ToolProjectile.Mode.ICE
                && element != ToolProjectile.Mode.LIGHTNING && element != ToolProjectile.Mode.WIND)
            throw new IllegalArgumentException("Fire Temple requires an elemental spell");
        entityData.set(ELEMENT, element.name().toLowerCase(java.util.Locale.ROOT));
    }

    public ToolProjectile.Mode element() {
        return ToolProjectile.Mode.valueOf(entityData.get(ELEMENT).toUpperCase(java.util.Locale.ROOT));
    }

    public String texturePath() { return "wizzrobe_" + entityData.get(ELEMENT); }

    @Override protected ToolProjectile.Mode spellMode() { return element(); }

    @Override protected float spellDamage() {
        return (float) getAttributeBaseValue(Attributes.ATTACK_DAMAGE);
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        int difficultyId = Math.max(1, difficulty.getDifficulty().getId());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(150.0D);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10.0D + 2.0D * difficultyId);
        setHealth(getMaxHealth());
        DungeonBossEquipment.equipArmor(this, difficultyId);
        return result;
    }

    @Override public boolean fireImmune() { return true; }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("element", entityData.get(ELEMENT));
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("element", net.minecraft.nbt.Tag.TAG_STRING))
            setElement(ToolProjectile.Mode.valueOf(tag.getString("element").toUpperCase(java.util.Locale.ROOT)));
    }
}
