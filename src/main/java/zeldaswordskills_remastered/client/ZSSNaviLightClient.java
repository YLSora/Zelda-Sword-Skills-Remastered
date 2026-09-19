package zeldaswordskills_remastered.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.registry.ZSSRegistries;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID, value = Dist.CLIENT)
public final class ZSSNaviLightClient {
    private static final Map<BlockPos, BlockState> REPLACED = new HashMap<>();
    private static final int UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static ClientLevel lightLevel;

    private ZSSNaviLightClient() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != lightLevel) {
            clear();
            lightLevel = minecraft.level;
        }
        if (lightLevel == null) return;
        if (!ZSSConfig.CLIENT.naviMovingLight.get()) {
            clear();
            return;
        }
        if (minecraft.isPaused()) return;

        Set<BlockPos> occupied = new HashSet<>();
        for (var entity : lightLevel.entitiesForRendering()) {
            if (entity instanceof NaviCreature navi && navi.isAlive()) {
                BlockPos pos = navi.blockPosition();
                if (lightLevel.hasChunkAt(pos) && !lightLevel.isOutsideBuildHeight(pos)
                        && lightLevel.getWorldBorder().isWithinBounds(pos)) occupied.add(pos);
            }
        }
        var previous = REPLACED.entrySet().iterator();
        while (previous.hasNext()) {
            var entry = previous.next();
            BlockPos pos = entry.getKey();
            if (!occupied.contains(pos) || !lightLevel.getBlockState(pos).is(ZSSRegistries.NAVI_LIGHT.get())) {
                restore(pos, entry.getValue());
                previous.remove();
            }
        }
        for (BlockPos pos : occupied) {
            if (REPLACED.containsKey(pos)) continue;
            BlockState existing = lightLevel.getBlockState(pos);
            boolean water = existing.is(Blocks.WATER) && existing.getFluidState().isSource();
            if (!existing.isAir() && !water) continue;
            BlockState light = ZSSRegistries.NAVI_LIGHT.get().defaultBlockState().setValue(LightBlock.WATERLOGGED, water);
            if (lightLevel.setBlock(pos, light, UPDATE_FLAGS)) REPLACED.put(pos, existing);
        }
    }

    private static void restore(BlockPos pos, BlockState state) {
        // A server block update takes precedence over the state saved for this local light.
        if (lightLevel.hasChunkAt(pos) && lightLevel.getBlockState(pos).is(ZSSRegistries.NAVI_LIGHT.get()))
            lightLevel.setBlock(pos, state, UPDATE_FLAGS);
    }

    private static void clear() {
        if (lightLevel != null) REPLACED.forEach(ZSSNaviLightClient::restore);
        REPLACED.clear();
        lightLevel = null;
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() == lightLevel) clear();
    }

    @SubscribeEvent
    public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() != lightLevel) return;
        var chunk = event.getChunk().getPos();
        REPLACED.keySet().removeIf(pos -> pos.getX() >> 4 == chunk.x && pos.getZ() >> 4 == chunk.z);
    }
}
