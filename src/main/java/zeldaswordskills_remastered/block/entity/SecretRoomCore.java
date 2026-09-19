package zeldaswordskills_remastered.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.ZSSWorldData;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

/** Discovery and fairy replenishment persist independently from one-shot container loot. */
public final class SecretRoomCore extends BlockEntity {
    private boolean fairyPool;
    private int roomRadius=2;
    public SecretRoomCore(BlockPos pos,BlockState state) { super(ZSSRegistries.SECRET_ROOM_CORE_ENTITY.get(),pos,state); }
    public boolean isFairyPool() { return fairyPool; }
    @Override public void load(CompoundTag tag) {
        super.load(tag); fairyPool=tag.getBoolean("fairy_pool");roomRadius=net.minecraft.util.Mth.clamp(tag.getInt("room_radius"),1,4);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putBoolean("fairy_pool",fairyPool);tag.putInt("room_radius",roomRadius);
    }
    public ResourceLocation instanceId(ServerLevel level) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID,"secret/"+level.dimension().location().getNamespace()+"/"
                +level.dimension().location().getPath()+"/"+worldPosition.getX()+"/"+worldPosition.getY()+"/"+worldPosition.getZ());
    }
    public static void tick(Level world,BlockPos pos,BlockState state,SecretRoomCore core) {
        if (!(world instanceof ServerLevel level) || level.getGameTime()%20!=0) return;
        var visitors = level.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class,
                new AABB(pos.offset(1-core.roomRadius,1,1-core.roomRadius),pos.offset(core.roomRadius,4,core.roomRadius)),player -> !player.isSpectator());
        if(visitors.isEmpty()) return;
        var data = ZSSWorldData.get(level);
        var id = core.instanceId(level);
        var progress = data.dungeonState(id);
        if(!progress.completed()) {
            ZSSCapabilities.get(visitors.get(0)).ifPresent(player -> {
                int count = player.discoverSecretRoom();
                ZSSAdvancementService.secretRoomDiscovered(visitors.get(0), count);
            });
            ZSSNetwork.syncPlayerData(visitors.get(0));
            level.playSound(null,pos,ZSSRegistries.SECRET_MEDLEY.get(),SoundSource.BLOCKS,1,1);
            data.setDungeonState(id,true,progress.cooldownUntil());
        }
        if(!core.fairyPool || level.getGameTime()<progress.cooldownUntil()) return;
        if(!level.getEntitiesOfClass(zeldaswordskills_remastered.entity.FairyCreature.class,new AABB(pos).inflate(8)).isEmpty()) return;
        for(int i=0;i<3;i++) {
            var fairy = ZSSRegistries.FAIRY.get().create(level);
            if(fairy!=null) { fairy.moveTo(pos.getX()+.5,pos.getY()+2.5,pos.getZ()+.5,0,0); level.addFreshEntity(fairy); }
        }
        data.setDungeonState(id,true,level.getGameTime()+(2+level.random.nextInt(6))*24000L);
    }
}
