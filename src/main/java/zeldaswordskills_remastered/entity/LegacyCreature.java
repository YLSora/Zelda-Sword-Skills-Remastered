package zeldaswordskills_remastered.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

import java.util.Optional;

/** Common state and vanilla lifecycle for the explicitly registered Zelda creature variants. */
public class LegacyCreature extends PathfinderMob implements zeldaswordskills_remastered.worldgen.DungeonBoss {
    private final Kind kind;
    private boolean whipLooted;
    private boolean bossRewardGranted;
    private DungeonType dungeonType;
    private BlockPos dungeonCorePos;

    public LegacyCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        applyKindAttributes();
        if (isBoss()) setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D)
                .add(Attributes.FLYING_SPEED, 0.25D);
    }

    private void applyKindAttributes() {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(kind.maxHealth());
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(kind.speed());
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(kind.attackDamage());
        getAttribute(Attributes.ARMOR).setBaseValue(kind.armor());
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(kind.knockbackResistance());
        getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(kind.followRange());
        getAttribute(Attributes.FLYING_SPEED).setBaseValue(Math.max(kind.speed(), 0.1D));
        setHealth(getMaxHealth());
    }

    public Kind kind() { return kind; }
    public boolean isBoss() { return kind == Kind.DARKNUT_BOSS || kind == Kind.WIZZROBE_GRAND || kind == Kind.FIRE_BOSS || kind == Kind.FOREST_BOSS || kind == Kind.WATER_BOSS; }
    public Optional<DungeonType> dungeonType() { return Optional.ofNullable(dungeonType); }
    public Optional<BlockPos> dungeonCorePos() { return Optional.ofNullable(dungeonCorePos); }
    public void linkDungeon(DungeonType type, BlockPos corePos) {
        if (!isBoss() || type == null || corePos == null) throw new IllegalStateException("Only bosses can link to a dungeon core");
        dungeonType = type;
        dungeonCorePos = corePos.immutable();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, SpawnGroupData spawnData, CompoundTag dataTag) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION
                && reason != MobSpawnType.REINFORCEMENT) setPersistenceRequired();
        return result;
    }

    @Override
    public boolean fireImmune() {
        return kind == Kind.BABA_FIRE || kind == Kind.KEESE_FIRE || kind == Kind.KEESE_CURSED
                || kind == Kind.CHU_RED || kind == Kind.OCTOROK_BOMB || kind == Kind.DARKNUT_STANDARD
                || kind == Kind.DARKNUT_MIGHTY
                || kind == Kind.WIZZROBE_FIRE || kind == Kind.WIZZROBE_ICE
                || kind == Kind.WIZZROBE_LIGHTNING || kind == Kind.WIZZROBE_WIND;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    public Component getName() {
        return hasCustomName() ? getCustomName()
                : Component.translatable("entity.zeldaswordskills_remastered." + kind.registryPath());
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return kind.family() != Family.FAIRY;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return dungeonCorePos == null && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    @Override public int getExperienceReward() { return kind.experience(); }

    public final boolean tryWhipLoot(Player player) {
        if (whipLooted || level().isClientSide) return false;
        ItemStack loot = LegacyCreatureDrops.whipLoot(this);
        if (loot.isEmpty()) return false;
        whipLooted = true;
        spawnAtLocation(loot);
        if (player instanceof ServerPlayer serverPlayer)
            ZSSAdvancementService.lootStolen(serverPlayer, loot.is(zeldaswordskills_remastered.registry.ZSSRegistries.getItem("knights_crest")));
        return true;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (isBoss()) grantBossReward();
    }

    private void grantBossReward() {
        if (bossRewardGranted || !(level() instanceof ServerLevel)) return;
        bossRewardGranted = true;
        if (DungeonController.completeBoss(this)) spawnAtLocation(LegacyCreatureDrops.bonusHeartOrb());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("zss_whip_looted", whipLooted);
        tag.putBoolean("zss_boss_reward", bossRewardGranted);
        if (dungeonType != null) tag.putString("zss_dungeon_id", dungeonType.id().toString());
        if (dungeonCorePos != null) tag.putLong("zss_dungeon_core", dungeonCorePos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        whipLooted = tag.getBoolean("zss_whip_looted");
        bossRewardGranted = tag.getBoolean("zss_boss_reward");
        net.minecraft.resources.ResourceLocation parsed = net.minecraft.resources.ResourceLocation.tryParse(tag.getString("zss_dungeon_id"));
        dungeonType = parsed == null ? null : DungeonType.byId(parsed).orElse(null);
        dungeonCorePos = tag.contains("zss_dungeon_core", net.minecraft.nbt.Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("zss_dungeon_core")) : null;
    }

    public enum Family { DARKNUT, FAIRY, CHU, BABA, KEESE, OCTOROK, SKULLTULA, WIZZROBE, FIRE_BOSS, FOREST_BOSS }

    public enum Kind {
        DARKNUT_STANDARD("darknut", "darknut_standard", Family.DARKNUT, 100, 10, .225, 10, .8, 40, 12),
        DARKNUT_MIGHTY("darknut_mighty", "darknut_standard", Family.DARKNUT, 200, 12, .225, 15, .8, 40, 20),
        FAIRY("fairy", "fairy", Family.FAIRY, 4, 0, .25, 0, 0, 12, 0),
        NAVI("navi", "fairy", Family.FAIRY, 4, 0, .28, 0, 0, 12, 0),
        CHU_RED("chu", "chu_red", Family.CHU, 16, 2, .22, 2, 0, 16, 4),
        CHU_GREEN("chu_green", "chu_green", Family.CHU, 16, 3, .22, 4, 0, 16, 4),
        CHU_BLUE("chu_blue", "chu_blue", Family.CHU, 16, 4, .22, 6, 0, 16, 5),
        CHU_YELLOW("chu_yellow", "chu_yellow", Family.CHU, 16, 5, .22, 8, 0, 16, 5),
        BABA_DEKU("baba_deku", "deku_baba", Family.BABA, 20, 3, 0, 0, 1, 16, 5),
        BABA_FIRE("baba_fire", "deku_baba", Family.BABA, 24, 4, 0, 2, 1, 40, 8),
        BABA_WITHERED("baba_withered", "deku_withered", Family.BABA, 8, 3, 0, 0, 1, 8, 2),
        KEESE_NORMAL("keese", "keese_base", Family.KEESE, 8, 2, .28, 0, 0, 12, 3),
        KEESE_FIRE("keese_fire", "keese_fire", Family.KEESE, 12, 2, .3, 0, 0, 12, 4),
        KEESE_ICE("keese_ice", "keese_ice", Family.KEESE, 12, 2, .3, 0, 0, 12, 4),
        KEESE_THUNDER("keese_thunder", "keese_thunder", Family.KEESE, 12, 2, .3, 0, 0, 12, 4),
        KEESE_CURSED("keese_cursed", "keese_cursed", Family.KEESE, 16, 3, .32, 2, 0, 16, 6),
        OCTOROK_NORMAL("octorok", "octorok1", Family.OCTOROK, 12, 2, .22, 2, .75, 16, 5),
        OCTOROK_BOMB("octorok_bomb", "octorok2", Family.OCTOROK, 16, 3, .22, 4, .75, 16, 7),
        SKULLTULA_NORMAL("skulltula", "skulltula", Family.SKULLTULA, 20, 3, .25, 4, .25, 16, 6),
        SKULLTULA_GOLD("skulltula_gold", "skulltula_gold", Family.SKULLTULA, 32, 4, .27, 10, .5, 20, 12),
        WIZZROBE_FIRE("wizzrobe", "wizzrobe_fire", Family.WIZZROBE, 30, 4, .25, 0, 1, 40, 8),
        WIZZROBE_ICE("wizzrobe_ice", "wizzrobe_ice", Family.WIZZROBE, 30, 4, .25, 0, 1, 40, 8),
        WIZZROBE_LIGHTNING("wizzrobe_lightning", "wizzrobe_lightning", Family.WIZZROBE, 30, 4, .25, 0, 1, 40, 8),
        WIZZROBE_WIND("wizzrobe_wind", "wizzrobe_wind", Family.WIZZROBE, 30, 4, .25, 0, 1, 40, 8),
        DARKNUT_BOSS("darknut_boss", "darknut_standard", Family.DARKNUT, 500, 15, .225, 20, .8, 48, 50),
        WIZZROBE_GRAND("wizzrobe_grand", "wizzrobe_fire_grand", Family.WIZZROBE, 300, 8, .25, 8, 1, 48, 50),
        FIRE_BOSS("fire_boss", "wizzrobe_fire", Family.FIRE_BOSS, 150, 12, .25, 0, 1, 40, 8),
        FOREST_BOSS("forest_boss", "skulltula_gold", Family.FOREST_BOSS, 100, 10, .27, 10, .5, 20, 12),
        WATER_BOSS("water_boss", "octorok2", Family.OCTOROK, 60, 4, .22, 2, .75, 16, 5);

        private final String registryPath;
        private final String texturePath;
        private final Family family;
        private final double maxHealth, attackDamage, speed, armor, knockbackResistance, followRange;
        private final int experience;

        Kind(String registryPath, String texturePath, Family family, double maxHealth, double attackDamage,
             double speed, double armor, double knockbackResistance, double followRange, int experience) {
            this.registryPath = registryPath; this.texturePath = texturePath; this.family = family;
            this.maxHealth = maxHealth; this.attackDamage = attackDamage; this.speed = speed; this.armor = armor;
            this.knockbackResistance = knockbackResistance; this.followRange = followRange; this.experience = experience;
        }

        public String registryPath() { return registryPath; }
        public String texturePath() { return texturePath; }
        public Family family() { return family; }
        public double maxHealth() { return maxHealth; }
        public double attackDamage() { return attackDamage; }
        public double speed() { return speed; }
        public double armor() { return armor; }
        public double knockbackResistance() { return knockbackResistance; }
        public double followRange() { return followRange; }
        public int experience() { return experience; }
    }
}
