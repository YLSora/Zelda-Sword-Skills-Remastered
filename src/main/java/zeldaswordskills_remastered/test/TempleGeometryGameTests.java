package zeldaswordskills_remastered.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.MechanismBlocks;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;
import zeldaswordskills_remastered.data.DungeonStructureDataProvider;
import zeldaswordskills_remastered.entity.WizzrobeCreature;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.world.ZSSWorldData;
import zeldaswordskills_remastered.worldgen.DungeonArena;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TempleGeometryGameTests {
    private TempleGeometryGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", batch = "zssTempleGeometry")
    public static void allTemplatesKeepTheirFurnishings(GameTestHelper helper) {
        for (DungeonType type : DungeonType.values()) for (int variant = 0; variant < 20; variant++) {
            var template = helper.getLevel().getStructureManager().get(DungeonStructureDataProvider.templateId(type, variant)).orElseThrow();
            helper.assertTrue(template.getSize().equals(new BlockPos((11 + variant % 5) * 3,
                    (9 + variant / 5) * 2, (11 + variant % 5) * 3)), "Wrong scaled size: " + type + "/" + variant);
            CompoundTag tag = template.save(new CompoundTag());
            var palette = tag.getList("palette", Tag.TAG_COMPOUND);
            Map<String, Integer> counts = new HashMap<>();
            int doors = 0;
            for (Tag entry : tag.getList("blocks", Tag.TAG_COMPOUND)) {
                var block = (CompoundTag) entry;
                String name = palette.getCompound(block.getInt("state")).getString("Name");
                if (block.contains("nbt")) counts.merge(name, 1, Integer::sum);
                if (name.contains(":door_boss_")) doors++;
                if (type == DungeonType.END) {
                    helper.assertTrue(!name.equals("minecraft:water")
                                    && !palette.getCompound(block.getInt("state")).getCompound("Properties")
                                            .getString("waterlogged").equals("true"),
                            "End Temple retained water: " + variant);
                    var pos = block.getList("pos", Tag.TAG_INT);
                    int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2);
                    int max = DungeonStructureDataProvider.roomSize(variant);
                    int roof = DungeonStructureDataProvider.roomHeight(variant) - 1;
                    boolean wall = y > 0 && y < roof && (x == 1 || x == max || z == 1 || z == max);
                    boolean ceiling = y == roof && x >= 1 && x <= max && z >= 1 && z <= max;
                    if (ceiling || wall && name.startsWith("zeldaswordskills_remastered:secret_stone_")) {
                        helper.assertTrue(name.equals("zeldaswordskills_remastered:secret_stone_" + (ceiling ? "purpur_block" : "end_stone_bricks")),
                                "End Temple shell material mismatch: " + variant + " " + pos + " " + name);
                        helper.assertTrue(palette.getCompound(block.getInt("state")).getCompound("Properties")
                                .getString("unbreakable").equals("true"), "End Temple shell must remain protected");
                    }
                }
                if (type == DungeonType.FIRE) {
                    helper.assertTrue(!name.equals("zeldaswordskills_remastered:secret_stone_end_stone"), "Fire retained its old shell");
                    var pos = block.getList("pos", Tag.TAG_INT);
                    int x = pos.getInt(0), y = pos.getInt(1), z = pos.getInt(2), max = template.getSize().getX() - 2;
                    boolean wall = y > 0 && y < template.getSize().getY() - 3 && (x == 1 || x == max || z == 1 || z == max);
                    if (wall && !name.equals("minecraft:air") && !name.equals("minecraft:iron_bars") && !name.contains(":door_boss_"))
                        helper.assertTrue(name.equals("zeldaswordskills_remastered:secret_stone_nether_wart_block"), "Fire wall uses another material: " + name);
                }
            }
            Map<String, Integer> expected = new HashMap<>();
            boolean chestCenter = type == DungeonType.DESERT || type == DungeonType.EARTH || type == DungeonType.WATER
                    || type == DungeonType.FOREST && variant % 5 != 0;
            int chests = (chestCenter ? 1 : 0) + (variant % 10 == 0 ? 1 : 0);
            if (chests > 0) expected.put("minecraft:chest", chests);
            int jars = counts.getOrDefault("zeldaswordskills_remastered:ceramic_jar", 0);
            helper.assertTrue(jars >= 3 && jars <= 11, "Missing or excessive jar furnishings: " + type + "/" + variant);
            expected.put("zeldaswordskills_remastered:ceramic_jar", jars);
            expected.put("zeldaswordskills_remastered:dungeon_core_" + (type == DungeonType.DESERT || type == DungeonType.WATER ? "sandstone" : "stone"), 1);
            if (type == DungeonType.FIRE || type == DungeonType.FOREST || type == DungeonType.ICE || type == DungeonType.END)
                expected.put("zeldaswordskills_remastered:chest_locked", 1);
            if (variant % 7 == 0 && !(type == DungeonType.EARTH && variant == 0) && !(type == DungeonType.END && variant == 7))
                expected.put("zeldaswordskills_remastered:chest_invisible", 1);
            if (type == DungeonType.FOREST && variant % 5 == 0) expected.put("zeldaswordskills_remastered:pedestal", 1);
            if (type == DungeonType.FIRE) expected.put("zeldaswordskills_remastered:sacred_flame_din", 1);
            if (type == DungeonType.END) expected.put("zeldaswordskills_remastered:sacred_flame_nayru", 1);
            if (type == DungeonType.EARTH) expected.put("zeldaswordskills_remastered:sacred_flame_farore", 1);
            helper.assertTrue(counts.equals(expected) && doors == 2, "Furnishing count changed: " + type + "/" + variant + " " + counts);
        }
        var stone = ZSSRegistries.SECRET_STONE_NETHER_WART_BLOCK.get();
        helper.assertTrue(DungeonStructureDataProvider.structureId(DungeonType.END).getPath().equals("end_dungeon")
                && DungeonStructureDataProvider.templateId(DungeonType.END, 0).getPath().equals("end_dungeon/room_00"),
                "End Temple resource paths were not renamed");
        helper.assertTrue(ZSSRegistries.SECRET_STONE_PURPUR_BLOCK.get().variant().replacement() == Blocks.PURPUR_BLOCK
                && ZSSRegistries.SECRET_STONE_END_STONE_BRICKS.get().variant().replacement() == Blocks.END_STONE_BRICKS,
                "End secret stones must reveal their matching vanilla blocks");
        helper.assertTrue(stone.variant().replacement() == Blocks.NETHER_WART_BLOCK
                && stone.asItem() == ZSSRegistries.SECRET_STONE_NETHER_WART_BLOCK_ITEM.get(), "New secret stone registration incomplete");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", batch = "zssTempleConfinement", timeoutTicks = 200)
    public static void templeCastersStayInsideRotatedRooms(GameTestHelper helper) {
        var level = helper.getLevel();
        Difficulty previous = level.getDifficulty();
        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        Random random = new Random(987654321L);
        try {
            level.getServer().setDifficulty(Difficulty.NORMAL, true);
            for (DungeonType type : List.of(DungeonType.FIRE, DungeonType.END))
                for (int variant : new int[]{0, 1, 19}) for (Rotation rotation : Rotation.values()) {
                    var template = level.getStructureManager().get(DungeonStructureDataProvider.templateId(type, variant)).orElseThrow();
                    int center = 1 + DungeonStructureDataProvider.roomSize(variant) / 2;
                    var pivot = new BlockPos(center, 0, center);
                    template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation).setRotationPivot(pivot), level.random, 2);
                    var core = (DungeonCore) level.getBlockEntity(origin.offset(pivot));
                    core.load(core.saveWithoutMetadata());
                    ZSSWorldData.get(level).setDungeonState(DungeonController.instanceId(level, core.getBlockPos()), false, 0L);
                    DungeonController.spawnBoss(level, core).orElseThrow();
                    AABB room = DungeonArena.interior(core);
                    for (var uuid : core.bossUuids()) {
                        var caster = (WizzrobeCreature) level.getEntity(uuid);
                        caster.setNoAi(true);
                        caster.setNoGravity(true);
                        caster.tick();
                        Vec3 inside = caster.position();
                        for (Vec3 outside : List.of(new Vec3(room.minX - 2, inside.y, inside.z),
                                new Vec3(room.maxX + 2, inside.y, inside.z), new Vec3(inside.x, inside.y, room.minZ - 2),
                                new Vec3(inside.x, inside.y, room.maxZ + 2), new Vec3(inside.x, room.maxY + 2, inside.z),
                                new Vec3(inside.x, room.minY - 2, inside.z))) {
                            helper.assertTrue(!caster.randomTeleport(outside.x, outside.y, outside.z, false), "Caster teleported outside its temple");
                            caster.setPos(outside);
                            caster.tick();
                            helper.assertTrue(DungeonArena.contains(core, caster.getBoundingBox()), "Caster was not returned after leaving its temple");
                        }
                        helper.assertTrue(!caster.randomTeleport(room.minX + .05, inside.y, inside.z, false), "Body clipped through wall at teleport endpoint");
                        int successful = caster.randomTeleport(core.getBlockPos().getX() + 1.5, room.minY + 5,
                                core.getBlockPos().getZ() + 1.5, false) ? 1 : 0;
                        for (int attempt = 0; attempt < 100; attempt++) {
                            double x = room.minX - 10 + random.nextDouble() * (room.getXsize() + 20);
                            double z = room.minZ - 10 + random.nextDouble() * (room.getZsize() + 20);
                            if (caster.randomTeleport(x, room.minY + 5, z, false)) successful++;
                            helper.assertTrue(DungeonArena.contains(core, caster.getBoundingBox()), "Random teleport escaped the rotated room");
                        }
                        helper.assertTrue(successful > 0, "Confinement disabled all in-room teleportation");
                        CompoundTag saved = caster.saveWithoutId(new CompoundTag());
                        var reloaded = (WizzrobeCreature) caster.getType().create(level);
                        reloaded.load(saved);
                        reloaded.setPos(room.maxX + 4, room.minY + 3, room.maxZ + 4);
                        reloaded.tick();
                        helper.assertTrue(DungeonArena.contains(core, reloaded.getBoundingBox()), "Reloaded caster lost its room boundary");
                        reloaded.discard();
                        caster.discard();
                    }
                    for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(44, 24, 44))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
        } finally {
            level.getEntitiesOfClass(WizzrobeCreature.class, new AABB(origin, origin.offset(48, 28, 48))).forEach(Entity::discard);
            level.getServer().setDifficulty(previous, true);
        }
        helper.succeed();
    }
}
