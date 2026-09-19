package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import zeldaswordskills_remastered.worldgen.DungeonBoss;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.Optional;

/** Desert Temple husk with fixed adult attributes and persistent encounter identity. */
public final class DesertBossCreature extends Husk implements DungeonBoss {
    private BlockPos dungeonCorePos;

    public DesertBossCreature(EntityType<? extends Husk> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Husk.createAttributes().add(Attributes.MAX_HEALTH, 80)
                .add(Attributes.ATTACK_DAMAGE, 8).add(Attributes.MOVEMENT_SPEED, .15)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0);
    }

    @Override public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType reason, SpawnGroupData group, CompoundTag tag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, group, tag);
        setBaby(false);
        setCanPickUpLoot(false);
        for (EquipmentSlot slot : EquipmentSlot.values()) setItemSlot(slot, ItemStack.EMPTY);
        // Vanilla zombie leader/baby rolls must not change the encounter's fixed attributes or population.
        for (var attribute : java.util.List.of(Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE,
                Attributes.MOVEMENT_SPEED, Attributes.SPAWN_REINFORCEMENTS_CHANCE)) getAttribute(attribute).removeModifiers();
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6 + 2 * Math.max(1, difficulty.getDifficulty().getId()));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.15);
        getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0);
        setHealth(80);
        return result;
    }

    @Override protected boolean convertsInWater() { return false; }
    @Override public boolean isBoss() { return true; }
    @Override public Optional<DungeonType> dungeonType() {
        return dungeonCorePos == null ? Optional.empty() : Optional.of(DungeonType.DESERT);
    }
    @Override public Optional<BlockPos> dungeonCorePos() { return Optional.ofNullable(dungeonCorePos); }
    @Override public void linkDungeon(DungeonType type, BlockPos pos) {
        if (type != DungeonType.DESERT) throw new IllegalArgumentException("Desert boss requires a Desert core");
        dungeonCorePos = pos.immutable();
    }
    @Override protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        DungeonController.completeBoss(this);
    }
    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (dungeonCorePos != null) {
            tag.putString("zss_dungeon_id", DungeonType.DESERT.id().toString());
            tag.putLong("zss_dungeon_core", dungeonCorePos.asLong());
        }
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        dungeonCorePos = DungeonType.DESERT.id().equals(ResourceLocation.tryParse(tag.getString("zss_dungeon_id")))
                && tag.contains("zss_dungeon_core", net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("zss_dungeon_core")) : null;
    }
}
