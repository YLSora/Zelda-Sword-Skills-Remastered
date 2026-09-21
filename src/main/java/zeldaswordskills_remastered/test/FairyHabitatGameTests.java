package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.entity.FairyCore;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.data.DungeonLootTables;
import zeldaswordskills_remastered.entity.FairyCreature;
import zeldaswordskills_remastered.progression.AcquisitionService;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.ZSSWorldData;

import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FairyHabitatGameTests {
    @GameTest(template="zssgametests.dungeon_empty", templateNamespace="minecraft", batch="zssHabitat", timeoutTicks=200)
    public static void poolRefillAndDiscovery(GameTestHelper helper) {
        verifyHabitat(helper, helper.getLevel(), helper.absolutePos(new BlockPos(16, 1, 16)));
    }

    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssHabitat", timeoutTicks=200)
    public static void rootRefillDiscoveryAndInteractions(GameTestHelper helper) {
        verifyHabitat(helper, helper.getLevel().getServer().getLevel(Level.NETHER), new BlockPos(5207, 150, 5207));
    }

    private static void verifyHabitat(GameTestHelper helper, ServerLevel level, BlockPos origin) {
        boolean root = level.dimension().equals(Level.NETHER);
        var forced = new ArrayList<ChunkPos>();
        for (int x = (origin.getX() - 16) >> 4; x <= (origin.getX() + 16) >> 4; x++) {
            for (int z = (origin.getZ() - 16) >> 4; z <= (origin.getZ() + 16) >> 4; z++) {
                if (level.setChunkForced(x, z, true)) forced.add(new ChunkPos(x, z));
                level.getChunk(x, z);
            }
        }
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(level.getGameTime() % 20 == 0, "Waiting for the habitat tick");
            for (int x = (origin.getX() - 16) >> 4; x <= (origin.getX() + 16) >> 4; x++)
                for (int z = (origin.getZ() - 16) >> 4; z <= (origin.getZ() + 16) >> 4; z++)
                    helper.assertTrue(level.areEntitiesLoaded(ChunkPos.asLong(x, z)), "Waiting for habitat entities");
        }).thenExecute(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-3, 1, -3), origin.offset(3, 8, 3)))
                level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(origin, (root ? ZSSRegistries.FAIRY_ROOT_CORE : ZSSRegistries.FAIRY_POOL_CORE).get().defaultBlockState());
            var core = (FairyCore) level.getBlockEntity(origin);
            var center = origin.above(FairyCore.spawnOffset(level));
            var area = new AABB(center).inflate(16);
            var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[Fairy_Habitat]"));
            player.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
            level.addFreshEntity(player);
            var data = ZSSWorldData.get(level);
            var id = core.instanceId(level);
            data.setDungeonState(id, false, 0);
            try {
                int discoveries = ZSSCapabilities.get(player).resolve().orElseThrow().secretRooms();
                FairyCore.tick(level, origin, core.getBlockState(), core);
                var fairies = level.getEntitiesOfClass(FairyCreature.class, area, fairy -> fairy.belongsToHabitat(origin));
                helper.assertTrue(fairies.size() == 3, "Habitat did not supply three fairies");
                helper.assertTrue(fairies.stream().allMatch(fairy -> fairy.canUpgradeEquipment()
                        && Math.abs(fairy.getY() - center.getY() - .5) < .01), "Wrong fairy height or upgrade eligibility");
                long cooldown = data.dungeonState(id).cooldownUntil();
                helper.assertTrue(cooldown == level.getGameTime() + 168000, "Refill must take exactly seven days");
                FairyCore.tick(level, origin, core.getBlockState(), core);
                helper.assertTrue(ZSSCapabilities.get(player).resolve().orElseThrow().secretRooms() == discoveries + 1,
                        "Discovery was lost or counted twice");
                core.load(core.saveWithoutMetadata());
                var saved = ZSSWorldData.load(data.save(new CompoundTag()));
                helper.assertTrue(saved.dungeonState(id).completed() && saved.dungeonState(id).cooldownUntil() == cooldown,
                        "Discovery or refill deadline lost on save");
                if (root) verifyRootInteractions(helper, level, player, origin, fairies);
                fairies.forEach(net.minecraft.world.entity.Entity::discard);
                FairyCore.tick(level, origin, core.getBlockState(), core);
                helper.assertTrue(level.getEntitiesOfClass(FairyCreature.class, area).isEmpty(), "Reload bypassed cooldown");
                data.setDungeonState(id, true, level.getGameTime());
                FairyCore.tick(level, origin, core.getBlockState(), core);
                helper.assertTrue(level.getEntitiesOfClass(FairyCreature.class, area).size() == 3, "Expired habitat did not refill");
                data.setDungeonState(id, true, level.getGameTime());
                FairyCore.tick(level, origin, core.getBlockState(), core);
                helper.assertTrue(level.getEntitiesOfClass(FairyCreature.class, area).size() == 3, "Surviving fairies were duplicated");
            } finally {
                player.discard();
                level.getEntitiesOfClass(FairyCreature.class, area).forEach(net.minecraft.world.entity.Entity::discard);
                level.removeBlock(origin, false);
                forced.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
            }
        }).thenSucceed();
    }

    private static void verifyRootInteractions(GameTestHelper helper, ServerLevel level, FakePlayer player,
                                               BlockPos origin, java.util.List<FairyCreature> fairies) {
        var fairy = fairies.get(0);
        Vec3 spawn = fairy.position();
        var tag = new CompoundTag();
        fairy.saveWithoutId(tag);
        fairy.load(tag);
        helper.assertTrue(fairy.belongsToHabitat(origin) && fairy.canUpgradeEquipment() && fairy.fireImmune(),
                "Root fairy lost its binding or fire protection after reload");
        fairy.moveTo(origin.getX() + 30, origin.getY(), origin.getZ());
        fairy.aiStep();
        helper.assertTrue(fairy.position().distanceToSqr(spawn) <= 64.000001D, "Root fairy must stay within eight blocks of its spawn");
        var hand = InteractionHand.MAIN_HAND;
        player.setItemInHand(hand, new ItemStack(ZSSRegistries.getItem("slingshot")));
        AcquisitionService.interact(player, fairy, hand);
        helper.assertTrue(fairy.isAlive() && player.getMainHandItem().is(ZSSRegistries.getItem("slingshot")), "Failed upgrade consumed fairy");
        player.getAbilities().instabuild = true;
        AcquisitionService.interact(player, fairy, hand);
        helper.assertTrue(fairy.isRemoved() && player.getMainHandItem().is(ZSSRegistries.getItem("scattershot")), "Root upgrade failed");
        player.getAbilities().instabuild = false;
        player.setItemInHand(hand, new ItemStack(Items.GLASS_BOTTLE));
        AcquisitionService.interact(player, fairies.get(1), hand);
        helper.assertTrue(fairies.get(1).isRemoved() && player.getMainHandItem().is(ZSSRegistries.getItem("fairy_bottle"))
                && !player.getMainHandItem().hasTag(), "Bottling retained upgrade eligibility");
        player.setHealth(1);
        player.getMainHandItem().use(level, player, hand);
        helper.assertTrue(player.getHealth() > 1 && player.getMainHandItem().is(Items.GLASS_BOTTLE), "Bottled fairy lost healing");
    }

    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssHabitat")
    public static void ordinaryFairyKeepsSpawnOriginOnReload(GameTestHelper helper) {
        var fairy = (FairyCreature) ZSSRegistries.FAIRY.get().create(helper.getLevel());
        Vec3 spawn = helper.absoluteVec(new Vec3(4, 10, 4));
        fairy.setPos(spawn);
        var tag = new CompoundTag();
        fairy.saveWithoutId(tag);
        fairy.setPos(spawn.add(4, 0, 0));
        fairy.saveWithoutId(tag);
        fairy.load(tag);
        helper.assertTrue(tag.getCompound("WanderOrigin").getDouble("X") == spawn.x,
                "Saving a wandering fairy must not move its spawn origin");
        fairy.setPos(spawn.add(30, 0, 0));
        fairy.aiStep();
        helper.assertTrue(fairy.position().distanceToSqr(spawn) <= 64.000001D,
                "An ordinary fairy must keep its spawn tether after reload");
        fairy.discard();
        helper.succeed();
    }

    @GameTest(template="zssgametests.empty", templateNamespace="minecraft", batch="zssHabitat")
    public static void retiredStructuresAreAbsent(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        for (String name : new String[]{"secret_rooms", "nether_secret_rooms", "nether_root_orb"}) {
            helper.assertTrue(registries.registryOrThrow(Registries.STRUCTURE).get(DungeonLootTables.id(name)) == null,
                    "Retired structure is still registered: " + name);
        }
        for (String name : new String[]{"secret_rooms", "nether_secret_rooms", "nether_root_orbs"}) {
            helper.assertTrue(registries.registryOrThrow(Registries.STRUCTURE_SET).get(DungeonLootTables.id(name)) == null,
                    "Retired structure set is still registered: " + name);
        }
        helper.succeed();
    }
}
