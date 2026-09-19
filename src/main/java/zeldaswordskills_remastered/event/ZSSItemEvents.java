package zeldaswordskills_remastered.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.ArrowLooseEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.item.EquipmentItem;
import zeldaswordskills_remastered.item.SpecialItems;
import zeldaswordskills_remastered.item.ZeldaCombatItems;
import zeldaswordskills_remastered.item.ProgressionItem;
import zeldaswordskills_remastered.item.LiftItems;
import zeldaswordskills_remastered.progression.AcquisitionService;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.config.ZSSConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Non-persistent equipment runtime. Fixed-duration effects avoid modifier accumulation across relogs. */
@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSItemEvents {
    private static final Map<UUID, Integer> HOVER_TICKS = new HashMap<>();

    private ZSSItemEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        consumeInventoryHearts(player);
        if (player.isAlive() && player.tickCount % 40 == 0) {
            ZSSCapabilities.get(player).ifPresent(data -> {
                if (!ZSSConfig.SERVER.masterMode.get() && data.restoreMagic(1.0F))
                    zeldaswordskills_remastered.network.ZSSNetwork.syncPlayerData(player);
            });
        }
        if ((player.tickCount & 3) != 0) return;
        if (player.getPersistentData().getLong("zss_power_piece_until") >= player.level().getGameTime())
            player.removeEffect(zeldaswordskills_remastered.registry.ZSSRegistries.STUN.get());
        for (ItemStack stack : player.getArmorSlots()) {
            if (!(stack.getItem() instanceof EquipmentItem equipment)) continue;
            apply(player, equipment.gear());
        }
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        if (!(feet.getItem() instanceof EquipmentItem equipment) || equipment.gear() != EquipmentItem.Gear.HOVER_BOOTS || player.onGround()) {
            HOVER_TICKS.remove(player.getUUID());
        }
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (player.tickCount % 64 == 0 && head.getItem() instanceof EquipmentItem equipment
                && equipment.gear() == EquipmentItem.Gear.COUPLES_MASK) {
            player.level().getEntitiesOfClass(net.minecraft.world.entity.npc.Villager.class, player.getBoundingBox().inflate(8.0D, 3.0D, 8.0D),
                    net.minecraft.world.entity.LivingEntity::isAlive).forEach(villager -> {
                        villager.getInventory().addItem(new ItemStack(net.minecraft.world.item.Items.BREAD, 3));
                        villager.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80));
                        if (player.level() instanceof net.minecraft.server.level.ServerLevel server)
                            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                                    villager.getX(), villager.getY() + 1.5D, villager.getZ(), 1, .2D, .2D, .2D, 0.0D);
                    });
        }
    }

    private static void consumeInventoryHearts(ServerPlayer player) {
        if (player.isCreative() || !player.isAlive()) return;
        boolean consumed = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            consumed |= ProgressionItem.consumeSmallHearts(player, player.getInventory().getItem(slot));
        }
        consumed |= ProgressionItem.consumeSmallHearts(player, player.containerMenu.getCarried());
        if (consumed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            if (player.containerMenu != player.inventoryMenu) player.inventoryMenu.broadcastChanges();
        }
    }

    private static void apply(ServerPlayer player, EquipmentItem.Gear gear) {
        switch (gear) {
            case GORON, GORON_MASK -> effect(player, MobEffects.FIRE_RESISTANCE, 10, 0);
            case ZORA, ZORA_MASK -> {
                effect(player, MobEffects.WATER_BREATHING, 10, 0);
                if (player.isInWater()) effect(player, MobEffects.DOLPHINS_GRACE, 10, 0);
                else effect(player, MobEffects.MOVEMENT_SLOWDOWN, 10, 0);
            }
            case HEAVY_BOOTS -> {
                effect(player, MobEffects.DAMAGE_RESISTANCE, 10, 0);
            }
            case HOVER_BOOTS -> hover(player);
            case PEGASUS_BOOTS, BUNNY_HOOD -> { }
            case HAWKEYE_MASK -> effect(player, MobEffects.NIGHT_VISION, 220, 0);
            case MASK_OF_SCENTS -> effect(player, MobEffects.LUCK, 10, 0);
            case STONE_MASK -> effect(player, MobEffects.INVISIBILITY, 10, 0);
            case GERUDO_MASK -> effect(player, MobEffects.HERO_OF_THE_VILLAGE, 10, 0);
            case GIBDO_MASK -> { effect(player, MobEffects.DAMAGE_RESISTANCE, 10, 0); effect(player, MobEffects.MOVEMENT_SLOWDOWN, 10, 0); }
            case KEATON_MASK -> effect(player, MobEffects.LUCK, 10, 1);
            case SKULL_MASK -> effect(player, MobEffects.NIGHT_VISION, 220, 0);
            case SPOOKY_MASK -> effect(player, MobEffects.DAMAGE_RESISTANCE, 10, 0);
            case MASK_OF_TRUTH -> player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class,
                    player.getBoundingBox().inflate(12.0D), net.minecraft.world.entity.LivingEntity::isAlive)
                    .forEach(monster -> monster.addEffect(new MobEffectInstance(MobEffects.GLOWING, 10, 0, true, false)));
            case DEKU_MASK -> effect(player, MobEffects.JUMP, 10, 1);
            case GIANTS_MASK -> {
                effect(player, MobEffects.DAMAGE_BOOST, 10, 1);
                effect(player, MobEffects.DAMAGE_RESISTANCE, 10, 1);
                effect(player, MobEffects.MOVEMENT_SLOWDOWN, 10, 0);
            }
            case FIERCE_DEITY_MASK -> {
                effect(player, MobEffects.DAMAGE_BOOST, 10, 2);
                effect(player, MobEffects.REGENERATION, 10, 0);
            }
            case MAJORA_MASK -> {
                effect(player, MobEffects.DAMAGE_BOOST, 10, 1);
                effect(player, MobEffects.MOVEMENT_SPEED, 10, 1);
            }
            default -> { }
        }
    }

    private static void hover(ServerPlayer player) {
        if (player.onGround()) {
            HOVER_TICKS.remove(player.getUUID());
            return;
        }
        int ticks = HOVER_TICKS.merge(player.getUUID(), 4, Integer::sum);
        if (ticks <= 60) effect(player, MobEffects.SLOW_FALLING, 10, 0);
    }

    private static void effect(ServerPlayer player, net.minecraft.world.effect.MobEffect effect, int duration, int amplifier) {
        player.addEffect(new MobEffectInstance(effect, duration, amplifier, true, false, true));
    }


    @SubscribeEvent
    public static void attacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        if (feet.getItem() instanceof EquipmentItem equipment && equipment.gear() == EquipmentItem.Gear.RUBBER_BOOTS
                && event.getSource().is(DamageTypes.LIGHTNING_BOLT)) event.setCanceled(true);
        if (event.getSource().is(DamageTypes.LIGHTNING_BOLT)
                && player.getPersistentData().getLong("zss_yellow_protection_until") >= player.level().getGameTime()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void shieldBlocked(net.minecraftforge.event.entity.living.ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getBlockedDamage() <= 0
                || !(player.getUseItem().getItem() instanceof ZeldaCombatItems.Shield shield)) return;
        Entity direct = event.getDamageSource().getDirectEntity();
        if (shield.reflective() && direct instanceof Projectile projectile) {
            projectile.setOwner(player);
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(-1.15D));
            projectile.hurtMarked = true;
        }
    }

    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        if (player.getMainHandItem().getItem() instanceof ZeldaCombatItems.Sword sword && sword.twoHanded()
                && !player.getOffhandItem().isEmpty()) event.setAmount(Math.min(1.0F, event.getAmount()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fairyRevive(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && SpecialItems.FairyBottle.releaseFairy(player, event.getSource())) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void death(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LiftItems.HeldBlock.settleInventory(player);
    }

    @SubscribeEvent
    public static void livingDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        AcquisitionService.recordTemperedSwordKill(player, event.getEntity());
        AcquisitionService.rollMobDrops(event.getEntity(), event.getEntity().getRandom())
                .forEach(stack -> event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(
                        event.getEntity().level(), event.getEntity().getX(), event.getEntity().getY(), event.getEntity().getZ(), stack)));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LiftItems.HeldBlock.settleInventory(player);
    }

    @SubscribeEvent
    public static void destroyedItem(PlayerDestroyItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getOriginal().getItem() instanceof ZeldaCombatItems.Sword sword)
                || sword.masterSword() || event.getOriginal().is(zeldaswordskills_remastered.registry.ZSSRegistries.BROKEN_SWORD.get())) return;
        ItemStack broken = new ItemStack(zeldaswordskills_remastered.registry.ZSSRegistries.BROKEN_SWORD.get());
        if (!player.getInventory().add(broken)) player.drop(broken, false);
    }

    @SubscribeEvent
    public static void arrowLoose(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getBow().getItem() instanceof ZeldaCombatItems.HeroBow)) return;
        ItemStack ammo = player.getProjectile(event.getBow());
        if (!(ammo.getItem() instanceof ZeldaCombatItems.ElementArrow arrow)) return;
        float cost = switch (arrow.kind()) {
            case FIRE, ICE -> 10.0F;
            case LIGHT -> 20.0F;
            default -> 0.0F;
        };
        if (cost == 0.0F) return;
        boolean paid = ZSSCapabilities.get(player).map(data -> data.consumeMagic(cost)).orElse(false);
        if (!paid) { event.setCanceled(true); return; }
        zeldaswordskills_remastered.network.ZSSNetwork.syncPlayerData(player);
    }

    @SubscribeEvent
    public static void arrowImpact(ProjectileImpactEvent event) {
        if (event.getImpactResult() != ProjectileImpactEvent.ImpactResult.DEFAULT
                && event.getImpactResult() != ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT) return;
        if (!(event.getProjectile() instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow)
                || arrow.level().isClientSide || arrow.getPersistentData().getBoolean("zss_arrow_used")) return;
        String kind = arrow.getPersistentData().getString("zss_arrow");
        if (kind.isEmpty()) return;
        arrow.getPersistentData().putBoolean("zss_arrow_used", true);
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof net.minecraft.world.entity.LivingEntity living) {
            switch (kind) {
                case "fire", "fire_bomb" -> living.setSecondsOnFire(5);
                case "ice" -> living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                case "light" -> living.hurt(arrow.damageSources().magic(), 5.0F);
                case "water_bomb" -> living.clearFire();
            }
        }
        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && arrow.level() instanceof net.minecraft.server.level.ServerLevel server) {
            net.minecraft.core.BlockPos block = blockHit.getBlockPos();
            net.minecraft.core.BlockPos adjacent = block.relative(blockHit.getDirection());
            if (kind.equals("fire") && server.getBlockState(adjacent).canBeReplaced()
                    && net.minecraft.world.level.block.BaseFireBlock.canBePlacedAt(server, adjacent, blockHit.getDirection()))
                server.setBlockAndUpdate(adjacent, net.minecraft.world.level.block.BaseFireBlock.getState(server, adjacent));
            else if (kind.equals("ice") && server.getBlockState(block).is(net.minecraft.world.level.block.Blocks.WATER))
                server.setBlockAndUpdate(block, net.minecraft.world.level.block.Blocks.ICE.defaultBlockState());
        }
        if (kind.endsWith("bomb")) {
            boolean fire = kind.equals("fire_bomb");
            zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions.ExplosionKind explosionKind = switch (kind) {
                case "fire_bomb" -> zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions.ExplosionKind.FIRE_BOMB;
                case "water_bomb" -> zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions.ExplosionKind.WATER_BOMB;
                default -> zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions.ExplosionKind.BOMB;
            };
            net.minecraft.core.BlockPos center = arrow.blockPosition();
            if (arrow.level() instanceof net.minecraft.server.level.ServerLevel server)
                net.minecraft.core.BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3)).forEach(pos -> {
                    zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions.explode(server, pos, explosionKind,
                            arrow.getOwner() instanceof ServerPlayer owner ? owner : null);
                    if (kind.equals("water_bomb") && server.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.FIRE))
                        server.removeBlock(pos, false);
                });
            arrow.level().explode(arrow, arrow.getX(), arrow.getY(), arrow.getZ(), 2.5F, fire, net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            arrow.discard();
        }
    }

    @SubscribeEvent
    public static void pickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getItem().getItem().getItem() instanceof ProgressionItem item)) return;
        ItemStack stack = event.getItem().getItem();
        if (!item.applyOnPickup(player, stack)) return;
        // Small hearts play their sound in the shared consumption path.
        if (item.kind() != ProgressionItem.Kind.SMALL_HEART) {
            player.playNotifySound(SoundEvents.ITEM_PICKUP,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.2F, 1.0F);
        }
        event.setCanceled(true);
        if (stack.isEmpty()) event.getItem().discard();
    }

    @SubscribeEvent
    public static void containerClosed(PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean found = false;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(ZSSRegistries.getItem("skill_orb")) || !stack.getOrCreateTag().getBoolean(ProgressionItem.CHEST_SKILL_TAG)) continue;
            stack.getOrCreateTag().remove(ProgressionItem.CHEST_SKILL_TAG);
            found = true;
        }
        if (found) zeldaswordskills_remastered.progression.ZSSAdvancementService.skillOrbFromChest(player);
    }
}
