package zeldaswordskills_remastered.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.worldgen.DungeonArena;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Teleporting elemental caster; the Grand form rotates elements and owns a boss bar. */
public class WizzrobeCreature extends LegacyCreature implements RangedAttackMob {
    private final ServerBossEvent bossBar;
    private int grandElement;
    private int teleportTicks;
    private Vec3 lastArenaPosition;

    public WizzrobeCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        bossBar = kind == Kind.WIZZROBE_GRAND
                ? new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS)
                : null;
        if (kind == Kind.WIZZROBE_GRAND) setNoGravity(true);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new RangedAttackGoal(this, .75D,
                kind() == Kind.WIZZROBE_GRAND ? 20 : 40, kind() == Kind.WIZZROBE_GRAND ? 40 : 60,
                kind() == Kind.WIZZROBE_GRAND ? 24.0F : 16.0F));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, .7D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override public void aiStep() {
        super.aiStep();
        if (kind() == Kind.WIZZROBE_GRAND) setDeltaMovement(getDeltaMovement().multiply(1, .8D, 1));
        if (!level().isClientSide && teleportTicks > 0 && --teleportTicks == 0) teleportNearTarget();
        if (bossBar != null) bossBar.setProgress(getHealth() / getMaxHealth());
    }

    private DungeonCore arena() {
        BlockPos pos = dungeonCorePos().orElse(null);
        if (pos == null || !(level() instanceof ServerLevel server) || !server.hasChunkAt(pos)) return null;
        return server.getBlockEntity(pos) instanceof DungeonCore core && !core.completed()
                && core.dungeonType().equals(dungeonType()) && core.bossUuids().contains(getUUID())
                && DungeonArena.validRoom(core) ? core : null;
    }

    @Override public boolean randomTeleport(double x, double y, double z, boolean particles) {
        if (dungeonCorePos().isEmpty()) return super.randomTeleport(x, y, z, particles);
        DungeonCore core = arena();
        if (core == null) return false;
        BlockPos landing = BlockPos.containing(x, y, z);
        if (!level().hasChunkAt(landing)) return false;
        // Vanilla randomTeleport descends to solid ground; validate that final body position too.
        while (landing.getY() > core.getBlockPos().getY()
                && !level().getBlockState(landing.below()).blocksMotion()) landing = landing.below();
        double landingY = landing.getY() + y - Math.floor(y);
        if (!DungeonArena.contains(core, getBoundingBox().move(x - getX(), landingY - getY(), z - getZ()))) return false;
        return super.randomTeleport(x, y, z, particles);
    }

    @Override public void tick() {
        super.tick();
        DungeonCore core = arena();
        if (core == null) return;
        if (!DungeonArena.contains(core, getBoundingBox())) {
            Vec3 escaped = position();
            boolean restored = false;
            if (lastArenaPosition != null) {
                setPos(lastArenaPosition);
                restored = DungeonArena.contains(core, getBoundingBox()) && level().noCollision(this);
            }
            if (!restored) {
                setPos(escaped);
                for (int corner = 0; corner < 4 && !restored; corner++)
                    restored = DungeonArena.positionAtCorner((ServerLevel) level(), core, this, corner);
            }
            if (restored) {
                getNavigation().stop();
                setDeltaMovement(Vec3.ZERO);
                fallDistance = 0;
                teleportTo(getX(), getY(), getZ());
            } else setPos(escaped);
        }
        if (DungeonArena.contains(core, getBoundingBox()) && level().noCollision(this)) lastArenaPosition = position();
    }

    @Override public void performRangedAttack(LivingEntity target, float distanceFactor) {
        ToolProjectile.Mode mode = spellMode();
        ToolProjectile spell = new ToolProjectile(ZSSRegistries.MAGIC_SPELL.get(), level(), this, mode).configureDamage(spellDamage());
        double dx = target.getX() - getX();
        double dy = target.getEyeY() - spell.getY();
        double dz = target.getZ() - getZ();
        float speed = .8F + .25F * level().getDifficulty().getId();
        spell.shoot(dx, dy, dz, speed, 3.0F);
        level().addFreshEntity(spell);
        teleportTicks = 6 + random.nextInt(9);
        if (kind() == Kind.WIZZROBE_GRAND) grandElement = (grandElement + 1 + random.nextInt(3)) % 4;
    }

    protected float spellDamage() {
        int difficulty = Math.max(1, level().getDifficulty().getId());
        return kind() == Kind.WIZZROBE_GRAND ? 20.0F + 4.0F * difficulty : 4.0F * difficulty;
    }

    protected ToolProjectile.Mode spellMode() {
        int element = kind() == Kind.WIZZROBE_GRAND ? grandElement : switch (kind()) {
            case WIZZROBE_FIRE -> 0; case WIZZROBE_ICE -> 1; case WIZZROBE_LIGHTNING -> 2; default -> 3;
        };
        return switch (element) {
            case 0 -> ToolProjectile.Mode.FIRE; case 1 -> ToolProjectile.Mode.ICE;
            case 2 -> ToolProjectile.Mode.LIGHTNING; default -> ToolProjectile.Mode.WIND;
        };
    }

    private void teleportNearTarget() {
        LivingEntity target = getTarget();
        if (target == null) return;
        double range = kind() == Kind.WIZZROBE_GRAND ? 16.0D : 10.0D;
        for (int attempt = 0; attempt < 12; attempt++) {
            double x = target.getX() + (random.nextDouble() - .5D) * range * 2;
            double y = target.getY() + random.nextInt(7) - 3;
            double z = target.getZ() + (random.nextDouble() - .5D) * range * 2;
            if (randomTeleport(x, y, z, true)) break;
        }
    }

    @Override public boolean hurt(DamageSource source, float amount) {
        ElementalDamage.Element incoming = ElementalDamage.from(source);
        ToolProjectile.Mode own = spellMode();
        if (incoming == ElementalDamage.Element.LIGHT) amount *= 2.0F;
        else if (incoming != ElementalDamage.Element.NONE && incoming.name().equals(own.name())) amount *= .25F;
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide && random.nextInt(kind() == Kind.WIZZROBE_GRAND ? 2 : 4) == 0) teleportTicks = 1;
        return hurt;
    }

    @Override public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player); if (bossBar != null) bossBar.addPlayer(player);
    }
    @Override public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player); if (bossBar != null) bossBar.removePlayer(player);
    }
    @Override public void setCustomName(net.minecraft.network.chat.Component name) {
        super.setCustomName(name); if (bossBar != null) bossBar.setName(getDisplayName());
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag); tag.putInt("grand_element", grandElement); tag.putInt("teleport_ticks", teleportTicks);
    }
    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag); grandElement = Math.floorMod(tag.getInt("grand_element"), 4); teleportTicks = Math.max(0, tag.getInt("teleport_ticks"));
    }
}
