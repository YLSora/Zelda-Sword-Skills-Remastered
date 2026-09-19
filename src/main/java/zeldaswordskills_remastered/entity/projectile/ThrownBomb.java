package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.entity.DarknutCreature;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID)
public final class ThrownBomb extends ThrowableItemProjectile {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ThrownBomb.class, EntityDataSerializers.INT);
    private int fuse = 40;
    private boolean griefsBlocks = true;
    private float damage;

    public ThrownBomb(EntityType<? extends ThrownBomb> type, Level level) { super(type, level); }
    public ThrownBomb(Level level, LivingEntity owner, BombKind kind) {
        super(ZSSRegistries.BOMB.get(), owner, level);
        entityData.set(KIND, kind.ordinal());
    }

    @Override protected void defineSynchedData() { super.defineSynchedData(); entityData.define(KIND, 0); }
    public BombKind bombKind() { return BombKind.values()[Math.max(0, Math.min(BombKind.values().length - 1, entityData.get(KIND)))]; }
    public ThrownBomb configureEncounterBomb(int fuseTicks) {
        fuse = Math.max(1, Math.min(40, fuseTicks));
        griefsBlocks = false;
        return this;
    }

    public ThrownBomb configureDamage(float amount) {
        damage = Math.max(0, amount);
        return this;
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void explosionDamage(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof ThrownBomb bomb) || bomb.damage <= 0
                || !event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return;
        // Keep vanilla explosion targeting, shielding and knockback; replace only its damage formula.
        float amount = bomb.damage * net.minecraft.world.level.Explosion.getSeenPercent(bomb.position(), event.getEntity());
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player && event.getSource().scalesWithDifficulty()) {
            amount = switch (event.getEntity().level().getDifficulty()) {
                case PEACEFUL -> 0;
                case EASY -> Math.min(amount / 2 + 1, amount);
                case NORMAL -> amount;
                case HARD -> amount * 1.5F;
            };
        }
        event.setAmount(amount);
    }

    @Override protected Item getDefaultItem() {
        return ZSSRegistries.getItem(switch (bombKind()) { case STANDARD -> "standard_bomb"; case FIRE -> "fire_bomb"; case WATER -> "water_bomb"; });
    }

    @Override protected void onHit(HitResult result) {
        if (result instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof DarknutCreature darknut
                && darknut.reflectBodyProjectile(this, entityHit.getLocation())) return;
        if (result.getType() != HitResult.Type.MISS) setDeltaMovement(getDeltaMovement().scale(0.2D));
    }

    @Override public void tick() {
        super.tick();
        if (!level().isClientSide && --fuse <= 0) explode();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("kind", bombKind().id().toString());
        tag.putInt("fuse", fuse);
        tag.putBoolean("griefs_blocks", griefsBlocks);
        tag.putFloat("damage", damage);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("kind")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("kind"));
            BombKind savedKind = null;
            for (BombKind kind : BombKind.values()) if (kind.id().equals(id)) savedKind = kind;
            if (savedKind == null) { discard(); return; }
            entityData.set(KIND, savedKind.ordinal());
        }
        fuse = Math.max(1, Math.min(40, tag.getInt("fuse")));
        griefsBlocks = !tag.contains("griefs_blocks") || tag.getBoolean("griefs_blocks");
        damage = Math.max(0, tag.getFloat("damage"));
    }

    private void explode() {
        if (!(level() instanceof ServerLevel level)) { discard(); return; }
        BombKind kind = bombKind();
        ZSSBlockInteractions.ExplosionKind interaction = switch (kind) {
            case STANDARD -> ZSSBlockInteractions.ExplosionKind.BOMB;
            case FIRE -> ZSSBlockInteractions.ExplosionKind.FIRE_BOMB;
            case WATER -> ZSSBlockInteractions.ExplosionKind.WATER_BOMB;
        };
        BlockPos center = blockPosition();
        if (griefsBlocks) BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3)).forEach(pos -> {
            ZSSBlockInteractions.explode(level, pos, interaction, getOwner() instanceof net.minecraft.server.level.ServerPlayer player ? player : null);
            if (kind == BombKind.WATER && level.getBlockState(pos).is(Blocks.FIRE)) level.removeBlock(pos, false);
        });
        level.explode(this, getX(), getY(), getZ(), kind == BombKind.FIRE ? 3.5F : 3.0F, kind == BombKind.FIRE,
                griefsBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        discard();
    }

    public enum BombKind {
        STANDARD("standard_bomb"), FIRE("fire_bomb"), WATER("water_bomb");

        private final ResourceLocation id;

        BombKind(String path) {
            id = ResourceLocation.fromNamespaceAndPath(zeldaswordskills_remastered.ZeldaSwordSkills_Remastered.MOD_ID, path);
        }

        public ResourceLocation id() { return id; }
    }
}
