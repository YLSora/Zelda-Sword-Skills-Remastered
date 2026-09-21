package zeldaswordskills_remastered.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.entity.FairyCreature;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.ZSSWorldData;

/** Fairy habitats share a seven-day cycle; chest loot is never replenished. */
public final class FairyCore extends BlockEntity {
    public static final long REFILL_TICKS = 7 * 24000L;

    public FairyCore(BlockPos pos, BlockState state) { super(ZSSRegistries.FAIRY_CORE_ENTITY.get(), pos, state); }

    public ResourceLocation instanceId(ServerLevel level) {
        return DungeonLootTables.id((level.dimension().equals(Level.NETHER) ? "fairy_root/" : "fairy_pool/") + level.dimension().location().getNamespace() + "/"
                + level.dimension().location().getPath() + "/" + worldPosition.getX() + "/"
                + worldPosition.getY() + "/" + worldPosition.getZ());
    }

    public static int spawnOffset(Level level) { return level.dimension().equals(Level.NETHER) ? 3 : 6; }

    public static void tick(Level world, BlockPos pos, BlockState state, FairyCore core) {
        if (!(world instanceof ServerLevel level) || (!level.dimension().equals(Level.OVERWORLD) && !level.dimension().equals(Level.NETHER))
                || level.getGameTime() % 20 != 0) return;
        BlockPos center = pos.above(spawnOffset(level));
        var visitors = level.getEntitiesOfClass(ServerPlayer.class,
                new AABB(center.offset(-12, -2, -12), center.offset(13, 8, 13)), player -> !player.isSpectator());
        if (visitors.isEmpty()) return;
        var data = ZSSWorldData.get(level);
        var id = core.instanceId(level);
        var progress = data.dungeonState(id);
        if (!progress.completed()) {
            var visitor = visitors.get(0);
            ZSSCapabilities.get(visitor).ifPresent(player ->
                    ZSSAdvancementService.secretRoomDiscovered(visitor, player.discoverSecretRoom()));
            ZSSNetwork.syncPlayerData(visitor);
            level.playSound(null, center, ZSSRegistries.SECRET_MEDLEY.get(), SoundSource.BLOCKS, 1, 1);
            data.setDungeonState(id, true, progress.cooldownUntil());
        }
        if (level.getGameTime() < progress.cooldownUntil()) return;
        // Empty entity queries are trustworthy only once every chunk around the tether is loaded.
        for (int x = (pos.getX() - 16) >> 4; x <= (pos.getX() + 16) >> 4; x++) {
            for (int z = (pos.getZ() - 16) >> 4; z <= (pos.getZ() + 16) >> 4; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z))) return;
            }
        }
        if (!level.getEntitiesOfClass(FairyCreature.class, new AABB(center).inflate(16),
                fairy -> fairy.belongsToHabitat(pos)).isEmpty()) return;
        for (int i = 0; i < 3; i++) {
            var fairy = (FairyCreature) ZSSRegistries.FAIRY.get().create(level);
            if (fairy == null) throw new IllegalStateException("Fairy entity factory returned null");
            fairy.bindToHabitat(pos);
            fairy.moveTo(center.getX() + .5 + (i - 1) * 2, center.getY() + .5, center.getZ() + .5);
            level.addFreshEntity(fairy);
        }
        data.setDungeonState(id, true, level.getGameTime() + REFILL_TICKS);
    }
}
