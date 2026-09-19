package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import zeldaswordskills_remastered.worldgen.DungeonBossEquipment;
import zeldaswordskills_remastered.worldgen.DungeonType;

/** Water Temple bomb Octorok with the frozen OceanBattle difficulty bonuses. */
public final class WaterBossCreature extends OctorokCreature {
    private int battleDifficulty = 1;

    public WaterBossCreature(EntityType<? extends LegacyCreature> type, Level level) {
        super(type, level, Kind.WATER_BOSS);
    }

    @Override public boolean ignoreExplosion() { return true; }

    @Override public boolean isInvulnerableTo(DamageSource source) {
        return source.is(DamageTypeTags.IS_EXPLOSION) || super.isInvulnerableTo(source);
    }

    @Override public void linkDungeon(DungeonType type, BlockPos pos) {
        if (type != DungeonType.WATER) throw new IllegalArgumentException("Water boss requires a Water core");
        super.linkDungeon(type, pos);
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        battleDifficulty = Math.max(1, difficulty.getDifficulty().getId());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0 * battleDifficulty);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2.0 + 2.0 * battleDifficulty);
        setHealth(getMaxHealth());
        DungeonBossEquipment.equipArmor(this, battleDifficulty);
        return result;
    }

    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        if (level().isClientSide) return;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        ThrownBomb bomb = new ThrownBomb(level(), this, ThrownBomb.BombKind.WATER)
                .configureEncounterBomb(24 - 4 * battleDifficulty)
                .configureDamage(4 + 2 * battleDifficulty);
        bomb.shoot(dx, target.getEyeY() - getEyeY() + Math.sqrt(dx * dx + dz * dz) * .15,
                dz, 1.0F, 14 - 4 * battleDifficulty);
        level().addFreshEntity(bomb);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("water_difficulty", battleDifficulty);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        battleDifficulty = Math.max(1, Math.min(3, tag.getInt("water_difficulty")));
    }
}
