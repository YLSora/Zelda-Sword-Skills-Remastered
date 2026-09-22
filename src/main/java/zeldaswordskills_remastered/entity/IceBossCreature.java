package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import zeldaswordskills_remastered.worldgen.DungeonBoss;
import zeldaswordskills_remastered.worldgen.DungeonBossEquipment;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.Optional;

/** Ice Temple illusioner, retaining vanilla spells, illusion synchronization and bow combat. */
public final class IceBossCreature extends Illusioner implements DungeonBoss {
    private DungeonType dungeonType;
    private BlockPos dungeonCorePos;

    public IceBossCreature(EntityType<? extends Illusioner> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setCanJoinRaid(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Illusioner.createAttributes().add(Attributes.ATTACK_DAMAGE, 12.0);
    }

    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ItemStack ammunition = getProjectile(getItemInHand(
                ProjectileUtil.getWeaponHoldingHand(this, item -> item instanceof BowItem)));
        var arrow = ProjectileUtil.getMobArrow(this, ammunition, distanceFactor);
        // Vanilla illusioner arrows ignore ATTACK_DAMAGE; use it while retaining bow enchantments.
        int power = getMainHandItem().getEnchantmentLevel(Enchantments.POWER_ARROWS);
        arrow.setBaseDamage(getAttributeValue(Attributes.ATTACK_DAMAGE) + (power > 0 ? .5 * power + .5 : 0));
        if (getMainHandItem().getItem() instanceof BowItem bow) arrow = bow.customArrow(arrow);
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        arrow.shoot(dx, dy + Math.sqrt(dx * dx + dz * dz) * .2, dz,
                1.6F, 14 - level().getDifficulty().getId() * 4);
        playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (random.nextFloat() * .4F + .8F));
        level().addFreshEntity(arrow);
    }

    @Override public boolean isBoss() { return true; }
    @Override public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return dungeonCorePos == null && super.removeWhenFarAway(distanceToClosestPlayer);
    }
    @Override public Optional<DungeonType> dungeonType() { return Optional.ofNullable(dungeonType); }
    @Override public Optional<BlockPos> dungeonCorePos() { return Optional.ofNullable(dungeonCorePos); }
    @Override public void linkDungeon(DungeonType type, BlockPos pos) {
        if (type != DungeonType.ICE) throw new IllegalArgumentException("Ice boss requires an Ice core");
        dungeonType = type;
        dungeonCorePos = pos.immutable();
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        int difficultyId = Math.max(1, difficulty.getDifficulty().getId());
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(75.0 + 25.0 * difficultyId);
        setHealth(getMaxHealth());
        setCanJoinRaid(false);
        DungeonBossEquipment.equipArmor(this, difficultyId);
        ItemStack bow = new ItemStack(Items.BOW);
        bow.enchant(Enchantments.POWER_ARROWS, 2 * difficultyId - 1);
        if (difficultyId > 1) bow.enchant(Enchantments.PUNCH_ARROWS, difficultyId - 1);
        if (difficultyId == 3) bow.enchant(Enchantments.FLAMING_ARROWS, 1);
        setItemSlot(EquipmentSlot.MAINHAND, bow);
        return result;
    }

    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        DungeonController.completeBoss(this);
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (dungeonType != null) tag.putString("zss_dungeon_id", dungeonType.id().toString());
        if (dungeonCorePos != null) tag.putLong("zss_dungeon_core", dungeonCorePos.asLong());
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("zss_dungeon_id"));
        dungeonType = parsed == null ? null : DungeonType.byId(parsed).filter(type -> type == DungeonType.ICE).orElse(null);
        dungeonCorePos = tag.contains("zss_dungeon_core", net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("zss_dungeon_core")) : null;
        setCanJoinRaid(false);
    }
}
