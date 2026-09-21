package zeldaswordskills_remastered.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.entity.projectile.ToolProjectile;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** Server-authoritative item actions for stage-nine ranged tools. */
public final class StageNineToolItem extends Item {
    public static final String UPGRADES_TAG = "upgrades";
    private final Kind kind;
    private final int level;

    public StageNineToolItem(Kind kind, int level, Properties properties) {
        super(kind == Kind.DEKU_NUT || kind == Kind.HOOKSHOT_UPGRADE ? properties : properties.durability(256));
        this.kind = kind;
        this.level = level;
    }

    public Kind kind() { return kind; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            if (kind == Kind.WHIP && this.level == 2) player.startUsingItem(hand);
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        boolean success = switch (kind) {
            case HOOKSHOT -> hook(serverPlayer, stack);
            case HOOKSHOT_UPGRADE -> upgrade(serverPlayer, stack);
            case BOOMERANG -> boomerang(serverPlayer);
            case DEKU_NUT -> nut(serverPlayer, stack);
            case DEKU_LEAF -> leaf(serverPlayer, stack);
            case ROCS_FEATHER -> feather(serverPlayer);
            case MEDALLION -> medallion(serverPlayer);
            case ROD -> rod(serverPlayer);
            case WHIP -> whip(serverPlayer);
            case SLINGSHOT -> slingshot(serverPlayer, stack);
        };
        if (!success) return InteractionResultHolder.fail(stack);
        stack.hurtAndBreak(1, player, entity -> entity.broadcastBreakEvent(hand));
        if (kind == Kind.WHIP && this.level == 2) player.startUsingItem(hand);
        player.getCooldowns().addCooldown(this, cooldown());
        return InteractionResultHolder.consume(stack);
    }

    private boolean hook(ServerPlayer player, ItemStack stack) {
        if (!player.level().getEntities(ZSSRegistries.HOOKSHOT.get(), player.getBoundingBox().inflate(64.0D),
                projectile -> projectile.getOwner() == player).isEmpty()) return false;
        double range = hasUpgrade(stack, "hookshot_extender") ? 24.0D : 12.0D;
        boolean multi = level == 3;
        boolean claw = level >= 2;
        ToolProjectile projectile = new ToolProjectile(ZSSRegistries.HOOKSHOT.get(), player.level(), player, ToolProjectile.Mode.HOOKSHOT)
                .configureHook(range, claw, multi);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.6F, 0.0F);
        player.level().addFreshEntity(projectile);
        return true;
    }

    private boolean upgrade(ServerPlayer player, ItemStack material) {
        String id = switch (level) { case 1 -> "hookshot_extender"; case 2 -> "claw_upgrade"; default -> "multi_hook_upgrade"; };
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack hook = player.getInventory().getItem(slot);
            ItemStack result = upgradeHook(hook, id);
            if (result.isEmpty()) continue;
            player.getInventory().setItem(slot, result);
            if (!player.getAbilities().instabuild) material.shrink(1);
            return true;
        }
        return false;
    }

    public static boolean isHook(ItemStack stack) {
        return stack.getItem() instanceof StageNineToolItem tool && tool.kind == Kind.HOOKSHOT;
    }

    public static ItemStack upgradeHook(ItemStack stack, String material) {
        if (!isHook(stack)) return ItemStack.EMPTY;
        if (material.equals("claw_upgrade") && stack.is(ZSSRegistries.getItem("hookshot")))
            return transform(stack, "stoneshot");
        if (material.equals("multi_hook_upgrade") && !stack.is(ZSSRegistries.getItem("multishot")))
            return transform(stack, "multishot");
        if (!material.equals("hookshot_extender") || hasUpgrade(stack, material)) return ItemStack.EMPTY;
        ItemStack result = stack.copyWithCount(1);
        var upgrades = result.getOrCreateTag().getList(UPGRADES_TAG, net.minecraft.nbt.Tag.TAG_STRING);
        upgrades.add(net.minecraft.nbt.StringTag.valueOf("zeldaswordskills_remastered:" + material));
        result.getOrCreateTag().put(UPGRADES_TAG, upgrades);
        return result;
    }

    public static ItemStack transform(ItemStack source, String output) {
        ItemStack result = new ItemStack(ZSSRegistries.getItem(output));
        if (source.hasTag()) result.setTag(source.getTag().copy());
        return result;
    }

    public static boolean hasUpgrade(ItemStack stack, String path) {
        if (!stack.hasTag()) return false;
        var list = stack.getTag().getList(UPGRADES_TAG, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals("zeldaswordskills_remastered:" + path)) return true;
        return false;
    }

    private boolean boomerang(ServerPlayer player) {
        if (!player.level().getEntities(ZSSRegistries.BOOMERANG.get(), player.getBoundingBox().inflate(64.0D),
                projectile -> projectile.getOwner() == player).isEmpty()) return false;
        ToolProjectile.Mode mode = level == 2 ? ToolProjectile.Mode.MAGIC_BOOMERANG : ToolProjectile.Mode.BOOMERANG;
        launch(player, ZSSRegistries.BOOMERANG.get(), mode, 1.1F, 0.0F, 0.0F, ZSSRegistries.BOOMERANG_SOUND.get());
        return true;
    }

    private boolean nut(ServerPlayer player, ItemStack stack) {
        launch(player, ZSSRegistries.SEEDSHOT.get(), ToolProjectile.Mode.NUT, 1.25F, 1.0F, 0.0F);
        consume(player, stack, 1);
        return true;
    }

    private boolean leaf(ServerPlayer player, ItemStack stack) {
        if (!consumeMagic(player, 2.0F)) return false;
        Vec3 look = player.getLookAngle();
        if (!player.onGround()) player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40));
        player.push(look.x * 0.35D, Math.max(0.12D, look.y * 0.2D), look.z * 0.35D);
        player.level().getEntities(player, player.getBoundingBox().expandTowards(look.scale(6.0D)).inflate(2.0D),
                entity -> (entity instanceof LivingEntity || entity instanceof ItemEntity)
                        && entity.position().subtract(player.getEyePosition()).normalize().dot(look) > 0.55D)
                .forEach(entity -> entity.push(look.x * 0.8D, Math.max(0.15D, look.y * 0.5D), look.z * 0.8D));
        player.serverLevel().sendParticles(ZSSRegistries.CYCLONE.get(), player.getX(), player.getEyeY(), player.getZ(),
                20, 0.6D, 0.5D, 0.6D, 0.08D);
        playToolSound(player, ZSSRegistries.WHIRLWIND.get(), 0.8F, 1.0F);
        return true;
    }

    private boolean feather(ServerPlayer player) {
        if (!player.onGround()) return false;
        player.push(0.0D, 0.62D, 0.0D);
        player.hurtMarked = true;
        return true;
    }

    private boolean medallion(ServerPlayer player) {
        if (!consumeMagic(player, 25.0F)) return false;
        ServerLevel level = player.serverLevel();
        AABB bounds = player.getBoundingBox().inflate(6.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity != player && entity.isAlive())) {
            target.hurt(player.damageSources().magic(), switch (this.level) { case 1 -> 10.0F; case 2 -> 6.0F; default -> 8.0F; });
            if (this.level == 1) target.setSecondsOnFire(5);
            if (this.level == 2) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
        }
        if (this.level == 3) BlockPos.betweenClosed(player.blockPosition().offset(-6, -2, -6), player.blockPosition().offset(6, 2, 6))
                .forEach(pos -> ZSSBlockInteractions.quake(level, pos, player));
        level.sendParticles(this.level == 1 ? ParticleTypes.FLAME : this.level == 2 ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD,
                player.getX(), player.getY() + 1, player.getZ(), 80, 4, 2, 4, 0.1D);
        playToolSound(player, this.level == 1 ? ZSSRegistries.MAGIC_FIRE.get()
                : this.level == 2 ? ZSSRegistries.MAGIC_ICE.get() : ZSSRegistries.WHIRLWIND.get(), 1.0F, 0.9F);
        return true;
    }

    private boolean rod(ServerPlayer player) {
        if (!consumeMagic(player, 10.0F)) return false;
        ToolProjectile.Mode mode = switch (level) {
            case 1 -> ToolProjectile.Mode.FIRE;
            case 2 -> ToolProjectile.Mode.ICE;
            default -> ToolProjectile.Mode.WIND;
        };
        SoundEvent sound = switch (mode) {
            case FIRE -> ZSSRegistries.MAGIC_FIRE.get();
            case ICE -> ZSSRegistries.MAGIC_ICE.get();
            case WIND -> ZSSRegistries.WHIRLWIND.get();
            default -> SoundEvents.ARROW_SHOOT;
        };
        launch(player, mode == ToolProjectile.Mode.WIND ? ZSSRegistries.CYCLONE_PROJECTILE.get() : ZSSRegistries.MAGIC_SPELL.get(),
                mode, 1.2F, 0.0F, 0.0F, sound);
        return true;
    }

    private boolean whip(ServerPlayer player) {
        if (!player.level().getEntities(ZSSRegistries.WHIP.get(), player.getBoundingBox().inflate(64.0D),
                projectile -> projectile.getOwner() == player).isEmpty()) return false;
        ToolProjectile projectile = new ToolProjectile(ZSSRegistries.WHIP.get(), player.level(), player, ToolProjectile.Mode.WHIP).configureWhip(level);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 0.0F);
        player.level().addFreshEntity(projectile);
        playToolSound(player, ZSSRegistries.WHIP_SOUND.get(), 0.8F, 1.0F);
        return true;
    }

    @Override public int getUseDuration(ItemStack stack) {
        return kind == Kind.WHIP && level == 2 ? 6000 : 0;
    }

    private boolean slingshot(ServerPlayer player, ItemStack stack) {
        int shots = switch (level) { case 1 -> 1; case 2 -> 3; default -> 5; };
        int ammo = findAmmo(player);
        if (ammo < 0 && !player.getAbilities().instabuild) return false;
        float spacing = shots == 1 ? 0.0F : 5.0F;
        for (int shot = 0; shot < shots; shot++) {
            float yawOffset = (shot - (shots - 1) * 0.5F) * spacing;
            int power = stack.getEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.POWER_ARROWS);
            ToolProjectile projectile = new ToolProjectile(ZSSRegistries.SEEDSHOT.get(), player.level(), player, ToolProjectile.Mode.SEED)
                    .configureDamage(ToolProjectile.Mode.SEED.damage() + (power > 0 ? power * 0.5F + 0.5F : 0));
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset, 0.0F, 1.45F, 1.0F);
            player.level().addFreshEntity(projectile);
        }
        playToolSound(player, SoundEvents.ARROW_SHOOT, 0.65F, 1.2F);
        if (!player.getAbilities().instabuild) player.getInventory().getItem(ammo).shrink(1);
        return true;
    }

    private static int findAmmo(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) if (player.getInventory().getItem(i).is(ZSSRegistries.getItem("bomb_flower_seed"))) return i;
        return -1;
    }

    private int cooldown() {
        return switch (kind) { case ROCS_FEATHER -> 20; case MEDALLION -> 80; case BOOMERANG -> level == 2 ? 70 : 50;
            case SLINGSHOT -> Math.max(4, 12 - level * 2); default -> 10; };
    }

    private static boolean consumeMagic(ServerPlayer player, float amount) {
        boolean consumed = ZSSCapabilities.get(player).map(data -> {
            if (!data.consumeMagic(amount)) return false;
            ZSSNetwork.syncPlayerData(player);
            return true;
        }).orElse(false);
        if (!consumed) playToolSound(player, ZSSRegistries.MAGIC_FAILURE.get(), 0.7F, 1.0F);
        return consumed;
    }

    private static void consume(Player player, ItemStack stack, int count) { if (!player.getAbilities().instabuild) stack.shrink(count); }
    private static void launch(ServerPlayer player, net.minecraft.world.entity.EntityType<ToolProjectile> type,
                               ToolProjectile.Mode mode, float velocity, float inaccuracy, float yawOffset) {
        launch(player, type, mode, velocity, inaccuracy, yawOffset, SoundEvents.ARROW_SHOOT);
    }

    private static void launch(ServerPlayer player, net.minecraft.world.entity.EntityType<ToolProjectile> type,
                               ToolProjectile.Mode mode, float velocity, float inaccuracy, float yawOffset, SoundEvent sound) {
        ToolProjectile projectile = new ToolProjectile(type, player.level(), player, mode);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset, 0.0F, velocity, inaccuracy);
        player.level().addFreshEntity(projectile);
        playToolSound(player, sound, 0.65F, sound == SoundEvents.ARROW_SHOOT ? 1.2F : 1.0F);
    }

    private static void playToolSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, volume, pitch);
    }
    private static BlockHitResult blockHit(Player player, double range) {
        Vec3 start = player.getEyePosition();
        return player.level().clip(new ClipContext(start, start.add(player.getLookAngle().scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
    }

    private static EntityHitResult entityHit(Player player, double range, java.util.function.Predicate<Entity> predicate) {
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        return ProjectileUtil.getEntityHitResult(player.level(), player, start, end,
                player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.0D), predicate);
    }

    public enum Kind { HOOKSHOT, HOOKSHOT_UPGRADE, BOOMERANG, DEKU_NUT, DEKU_LEAF, ROCS_FEATHER, MEDALLION, ROD, WHIP, SLINGSHOT }
}
