package zeldaswordskills_remastered.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class MagicMirrorService {
    public static final int CHARGE_TICKS = 60;
    // world_warp.ogg is 3.946395 seconds, rounded up to the next server tick.
    public static final int SOUND_TICKS = 79;
    public static final int TELEPORT_DELAY = SOUND_TICKS + 20;
    private static final int SEARCH_RADIUS = 16;
    private static final Map<UUID, Transition> ACTIVE = new HashMap<>();

    private record Transition(ResourceKey<Level> source, Vec3 origin, int deadline) {}

    private MagicMirrorService() {}

    public static boolean canUse(Player player) {
        return player.isAlive() && !player.isPassenger() && !player.isSleeping()
                && (player.level().dimension() == Level.OVERWORLD || player.level().dimension() == Level.NETHER)
                && (!(player instanceof ServerPlayer) || !ACTIVE.containsKey(player.getUUID()));
    }

    public static void start(ServerPlayer player) {
        if (!canUse(player)) return;
        ACTIVE.put(player.getUUID(), new Transition(player.level().dimension(), player.position(),
                player.server.getTickCount() + TELEPORT_DELAY));
        player.getCooldowns().addCooldown(ZSSRegistries.getItem("magic_mirror"), TELEPORT_DELAY + 1);
        ZSSNetwork.syncMirrorState(player, true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        Transition transition = ACTIVE.get(player.getUUID());
        if (transition == null) return;
        if (!player.isAlive() || player.level().dimension() != transition.source()) {
            cancel(player);
            return;
        }
        if (player.server.getTickCount() < transition.deadline()) return;
        ACTIVE.remove(player.getUUID());
        ServerLevel destination = player.server.getLevel(transition.source() == Level.OVERWORLD ? Level.NETHER : Level.OVERWORLD);
        try {
            BlockPos landing = destination == null ? null : findLanding(destination,
                    scaledTarget(player.serverLevel(), destination, transition.origin()));
            if (landing == null || !net.minecraftforge.common.ForgeHooks.onTravelToDimension(player, destination.dimension())) {
                player.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.mirror_no_destination"), true);
                return;
            }
            player.stopRiding();
            player.teleportTo(destination, landing.getX() + 0.5D, landing.getY(), landing.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0.0F;
        } finally {
            ZSSNetwork.syncMirrorState(player, false);
        }
    }

    public static BlockPos scaledTarget(ServerLevel source, ServerLevel destination, Vec3 origin) {
        double scale = DimensionType.getTeleportationScale(source.dimensionType(), destination.dimensionType());
        var border = destination.getWorldBorder();
        return BlockPos.containing(Mth.clamp(origin.x * scale, border.getMinX() + 2, border.getMaxX() - 2),
                Mth.clamp(origin.y, destination.getMinBuildHeight() + 1, maximumFeetY(destination)),
                Mth.clamp(origin.z * scale, border.getMinZ() + 2, border.getMaxZ() - 2));
    }

    private static int maximumFeetY(ServerLevel level) {
        return Math.min(level.getMaxBuildHeight(), level.getMinBuildHeight() + level.dimensionType().logicalHeight()) - 2;
    }

    public static BlockPos findLanding(ServerLevel level, BlockPos target) {
        BlockPos best = null;
        double distance = Double.MAX_VALUE;
        BlockPos.MutableBlockPos candidate = new BlockPos.MutableBlockPos();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                for (int y = level.getMinBuildHeight() + 1; y <= maximumFeetY(level); y++) {
                    candidate.set(target.getX() + x, y, target.getZ() + z);
                    double nextDistance = candidate.distSqr(target);
                    if (nextDistance < distance && safe(level, candidate)) {
                        best = candidate.immutable();
                        distance = nextDistance;
                        if (distance == 0) return best;
                    }
                }
            }
        }
        if (best != null) return best;

        // Prefer an open volume before excavating a small landing space.
        for (boolean clearOnly : new boolean[]{true, false}) {
            for (int offset = 0; offset <= maximumFeetY(level) - level.getMinBuildHeight(); offset++) {
                for (int sign : new int[]{1, -1}) {
                    int y = target.getY() + offset * sign;
                    if (y <= level.getMinBuildHeight() || y > maximumFeetY(level)) continue;
                    BlockPos feet = new BlockPos(target.getX(), y, target.getZ());
                    if (canBuildPlatform(level, feet, clearOnly)) return buildPlatform(level, feet);
                }
            }
        }
        return null;
    }

    public static boolean safe(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() > maximumFeetY(level)
                || !level.getWorldBorder().isWithinBounds(new AABB(feet).inflate(0, 1, 0))) return false;
        var floor = level.getBlockState(feet.below());
        if ((!floor.isFaceSturdy(level, feet.below(), Direction.UP) && !(floor.getBlock() instanceof LeavesBlock))
                || !level.getFluidState(feet.below()).isEmpty()) return false;
        var pathType = WalkNodeEvaluator.getBlockPathTypeStatic(level, feet.mutable());
        return (pathType == BlockPathTypes.WALKABLE || (floor.getBlock() instanceof LeavesBlock && pathType == BlockPathTypes.OPEN))
                && level.noCollision(new AABB(feet.getX() + 0.2D, feet.getY(), feet.getZ() + 0.2D,
                        feet.getX() + 0.8D, feet.getY() + 1.8D, feet.getZ() + 0.8D))
                && level.getFluidState(feet).isEmpty() && level.getFluidState(feet.above()).isEmpty()
                && !hazardNearby(level, feet);
    }

    private static boolean hazardNearby(ServerLevel level, BlockPos feet) {
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            var state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty() || WalkNodeEvaluator.isBurningBlock(state)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                    || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)) return true;
        }
        return false;
    }

    private static boolean canBuildPlatform(ServerLevel level, BlockPos feet, boolean clearOnly) {
        if (!level.getWorldBorder().isWithinBounds(new AABB(feet.offset(-1, -1, -1), feet.offset(2, 2, 2)))) return false;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-2, -1, -2), feet.offset(2, 2, 2))) {
            var state = level.getBlockState(pos);
            boolean inside = Math.abs(pos.getX() - feet.getX()) <= 1 && Math.abs(pos.getZ() - feet.getZ()) <= 1;
            if (inside && (state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0)) return false;
            if (pos.getY() >= feet.getY() && (clearOnly || !inside)
                    && (!state.getFluidState().isEmpty() || WalkNodeEvaluator.isBurningBlock(state))) return false;
            if (clearOnly && inside && pos.getY() >= feet.getY() && !state.isAir()) return false;
        }
        return true;
    }

    private static BlockPos buildPlatform(ServerLevel level, BlockPos feet) {
        var platform = level.dimension() == Level.NETHER ? Blocks.OBSIDIAN.defaultBlockState()
                : Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            level.setBlock(feet.offset(x, -1, z), platform, Block.UPDATE_ALL);
            for (int y = 0; y <= 2; y++) level.setBlock(feet.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return feet;
    }

    private static void cancel(ServerPlayer player) {
        if (ACTIVE.remove(player.getUUID()) != null) ZSSNetwork.syncMirrorState(player, false);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void dimensionChanged(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }

    @SubscribeEvent
    public static void died(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) cancel(player);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
