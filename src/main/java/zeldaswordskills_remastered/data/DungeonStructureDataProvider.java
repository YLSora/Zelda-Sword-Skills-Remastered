package zeldaswordskills_remastered.data;

import com.google.common.hash.Hashing;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.DungeonBlocks;
import zeldaswordskills_remastered.block.LockedChestBlock;
import zeldaswordskills_remastered.block.LockedDoorBlock;
import zeldaswordskills_remastered.block.MechanismBlocks;
import zeldaswordskills_remastered.block.PedestalBlock;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/** Generates the seven data-driven boss rooms from the original 1.8.9 RoomBoss rules. */
public final class DungeonStructureDataProvider implements DataProvider {
    public static final int MIN_ROOM_SIZE = 9;
    public static final int MAX_ROOM_SIZE = 13;
    public static final int MIN_ROOM_HEIGHT = 7;
    public static final int MAX_ROOM_HEIGHT = 10;
    public static final int VARIANT_COUNT = (MAX_ROOM_SIZE - MIN_ROOM_SIZE + 1)
            * (MAX_ROOM_HEIGHT - MIN_ROOM_HEIGHT + 1);

    public static int roomSize(int variant) {
        return (MIN_ROOM_SIZE + variant % 5 + 2) * 3 - 2;
    }

    public static int roomHeight(int variant) {
        return (MIN_ROOM_HEIGHT + variant / 5 + 2) * 2 - 2;
    }

    private final PackOutput.PathProvider structures;
    private final PackOutput.PathProvider structureDefinitions;
    private final PackOutput.PathProvider structureSets;
    private final PackOutput.PathProvider templatePools;

    public DungeonStructureDataProvider(PackOutput output) {
        structures = output.createPathProvider(PackOutput.Target.DATA_PACK, "structures");
        structureDefinitions = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/structure");
        structureSets = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/structure_set");
        templatePools = output.createPathProvider(PackOutput.Target.DATA_PACK, "worldgen/template_pool");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (DungeonType type : DungeonType.values()) {
            for (int variant = 0; variant < VARIANT_COUNT; variant++) {
                writes.add(saveNbt(output, createRoom(type, variant), structures.file(templateId(type, variant), "nbt")));
            }
            writes.add(DataProvider.saveStable(output, templatePool(type), templatePools.json(poolId(type))));
            writes.add(DataProvider.saveStable(output, structure(type), structureDefinitions.json(structureId(type))));
        }
        writes.add(DataProvider.saveStable(output, structureSet(List.of(DungeonType.DESERT, DungeonType.EARTH,
                DungeonType.FOREST, DungeonType.ICE, DungeonType.WATER), 19178327, false),
                structureSets.json(id("boss_dungeons"))));
        writes.add(DataProvider.saveStable(output, structureSet(List.of(DungeonType.FIRE), 19385479, true),
                structureSets.json(id("nether_boss_dungeons"))));
        writes.add(DataProvider.saveStable(output, structureSet(List.of(DungeonType.END), 19593761, true),
                structureSets.json(id("end_boss_dungeons"))));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "ZeldaSwordSkills_Remastered legacy boss dungeons";
    }

    public static ResourceLocation structureId(DungeonType type) {
        return id(type.getSerializedName() + "_dungeon");
    }

    public static ResourceLocation templateId(DungeonType type, int variant) {
        return id(structureId(type).getPath() + "/room_" + String.format(java.util.Locale.ROOT, "%02d", variant));
    }

    private static ResourceLocation poolId(DungeonType type) {
        return id(structureId(type).getPath() + "/start_pool");
    }

    private static JsonObject templatePool(DungeonType type) {
        JsonObject pool = new JsonObject();
        pool.addProperty("fallback", "minecraft:empty");
        JsonArray elements = new JsonArray();
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            JsonObject entry = new JsonObject();
            entry.addProperty("weight", 1);
            JsonObject element = new JsonObject();
            element.addProperty("element_type", "minecraft:single_pool_element");
            element.addProperty("location", templateId(type, variant).toString());
            element.addProperty("processors", "minecraft:empty");
            element.addProperty("projection", "rigid");
            entry.add("element", element);
            elements.add(entry);
        }
        pool.add("elements", elements);
        return pool;
    }

    private static JsonObject structure(DungeonType type) {
        JsonObject structure = new JsonObject();
        structure.addProperty("type", type == DungeonType.END ? id("end_temple").toString() : id("temple").toString());
        structure.addProperty("biomes", "#" + type.biomeTag().location());
        structure.addProperty("step", "surface_structures");
        structure.add("spawn_overrides", new JsonObject());
        structure.addProperty("terrain_adaptation", type == DungeonType.FIRE ? "none" : "beard_thin");
        structure.addProperty("start_pool", poolId(type).toString());
        if (type == DungeonType.END) return structure;
        structure.addProperty("size", 1);
        JsonObject startHeight = new JsonObject();
        // Single-pool pieces put their floor one block below the Jigsaw start height.
        startHeight.addProperty("absolute", type == DungeonType.FIRE ? 129 : 0);
        structure.add("start_height", startHeight);
        structure.addProperty("use_expansion_hack", false);
        if (type != DungeonType.FIRE) {
            structure.addProperty("project_start_to_heightmap",
                    type == DungeonType.WATER ? "OCEAN_FLOOR_WG" : "WORLD_SURFACE_WG");
        }
        structure.addProperty("max_distance_from_center", 32);
        return structure;
    }

    private static JsonObject structureSet(List<DungeonType> types, int salt, boolean rare) {
        JsonObject set = new JsonObject();
        JsonArray structures = new JsonArray();
        for (DungeonType type : types) {
            JsonObject entry = new JsonObject();
            entry.addProperty("structure", structureId(type).toString());
            entry.addProperty("weight", 1);
            structures.add(entry);
        }
        set.add("structures", structures);
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("salt", salt);
        placement.addProperty("spacing", rare ? 48 : 24);
        placement.addProperty("separation", rare ? 16 : 8);
        if (rare) placement.addProperty("frequency", 0.125F);
        set.add("placement", placement);
        return set;
    }

    private static CompoundTag createRoom(DungeonType type, int variant) {
        int roomSize = MIN_ROOM_SIZE + variant % (MAX_ROOM_SIZE - MIN_ROOM_SIZE + 1);
        int roomHeight = MIN_ROOM_HEIGHT + variant / (MAX_ROOM_SIZE - MIN_ROOM_SIZE + 1);
        // Resolve the furnishing plan before resizing, including failed random jar placements.
        RoomBuilder plan = new RoomBuilder(type, variant, roomSize, roomHeight);
        plan.buildLayout();
        RoomBuilder room = new RoomBuilder(type, variant, roomSize(variant), roomHeight(variant));
        room.buildLayout();
        room.applyFurnishings(plan);
        if (type == DungeonType.EARTH) {
            room.set(room.center - 1, 1, room.center, room.material);
            room.set(room.center - 1, 2, room.center, ZSSRegistries.SACRED_FLAME_FARORE.get().defaultBlockState(),
                    room.blockEntity("sacred_flame_farore"));
        }
        return room.build();
    }

    private static CompletableFuture<?> saveNbt(CachedOutput output, CompoundTag tag, Path path) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, bytes);
            byte[] encoded = bytes.toByteArray();
            output.writeIfNeeded(path, encoded, Hashing.sha256().hashBytes(encoded));
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }

    private record Placed(BlockState state, CompoundTag nbt) {
    }

    private static final class RoomBuilder {
        private final DungeonType type;
        private final int variant;
        private final int roomSize;
        private final int roomHeight;
        private final int max;
        private final int center;
        private final int totalSize;
        private final Random random;
        private final Map<BlockPos, Placed> blocks = new LinkedHashMap<>();
        private final BlockState shell;
        private final BlockState material;
        private final BlockState slab;
        private final BlockState stairs;
        private final boolean ocean;
        private final boolean submerged;
        private final Direction doorSide;

        private RoomBuilder(DungeonType type, int variant, int roomSize, int roomHeight) {
            this.type = type;
            this.variant = variant;
            this.roomSize = roomSize;
            this.roomHeight = roomHeight;
            max = roomSize;
            center = 1 + roomSize / 2;
            totalSize = roomSize + 2;
            random = new Random(0x5A17D00DL + type.ordinal() * 341873128712L + variant * 132897987541L);
            shell = secretStone(type).defaultBlockState().setValue(MechanismBlocks.SecretStone.UNBREAKABLE, true);
            material = material(type).defaultBlockState();
            slab = slab(type).defaultBlockState().setValue(SlabBlock.TYPE,
                    net.minecraft.world.level.block.state.properties.SlabType.BOTTOM);
            stairs = stairs(type).defaultBlockState();
            ocean = type == DungeonType.WATER;
            submerged = ocean || type == DungeonType.END;
            doorSide = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}[variant % 4];
        }

        private CompoundTag build() {
            return structureTag();
        }

        private void buildLayout() {
            buildShellAndInterior();
            placeDoorAndCore();
            placeCenterPiece();
            placePillars();
            placeChandelier();
            placeLedge();
            placeParapet();
            placeWindows();
            placeTerrain();
            placeChestsAndJars();
        }

        private void applyFurnishings(RoomBuilder plan) {
            blocks.replaceAll((pos, placed) -> isFurnishing(placed)
                    ? new Placed(ocean && pos.getY() < roomHeight - 1 ? Blocks.WATER.defaultBlockState()
                    : Blocks.AIR.defaultBlockState(), null) : placed);
            plan.blocks.forEach((pos, placed) -> {
                if (!isFurnishing(placed)) return;
                int x = resizeCoordinate(pos.getX(), plan);
                int z = resizeCoordinate(pos.getZ(), plan);
                int y = pos.getY() >= plan.roomHeight ? roomHeight + pos.getY() - plan.roomHeight : pos.getY();
                set(x, y, z, placed.state(), placed.nbt());
                // Keep each single-block furnishing accessible on its new floor or platform.
                if (isReplaceable(x, y - 1, z)) set(x, y - 1, z, material);
                set(x, y + 1, z, ocean && y < roomHeight - 1 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            });
        }

        private static boolean isFurnishing(Placed placed) {
            return placed.nbt() != null && !(placed.state().getBlock() instanceof DungeonBlocks.Core);
        }

        private int resizeCoordinate(int coordinate, RoomBuilder plan) {
            if (coordinate == plan.center) return center;
            if (coordinate < plan.center)
                return 1 + Math.round((coordinate - 1) * (center - 1.0F) / (plan.center - 1));
            return center + Math.round((coordinate - plan.center) * (max - (float) center) / (plan.max - plan.center));
        }

        private void buildShellAndInterior() {
            for (int y = 0; y < roomHeight; y++) {
                for (int z = 1; z <= max; z++) {
                    for (int x = 1; x <= max; x++) {
                        boolean edge = y == 0 || y == roomHeight - 1 || x == 1 || x == max || z == 1 || z == max;
                        BlockState exterior = shell;
                        if (type == DungeonType.END && y > 0) {
                            exterior = (y == roomHeight - 1 ? ZSSRegistries.SECRET_STONE_PURPUR_BLOCK
                                    : ZSSRegistries.SECRET_STONE_END_STONE_BRICKS).get().defaultBlockState()
                                    .setValue(MechanismBlocks.SecretStone.UNBREAKABLE, true);
                        }
                        set(x, y, z, edge ? exterior : Blocks.AIR.defaultBlockState());
                    }
                }
            }
            if (ocean) {
                fill(2, 1, 2, max - 1, roomHeight - 2, max - 1, Blocks.WATER.defaultBlockState());
            }
        }

        private void placeDoorAndCore() {
            int doorY = submerged ? 2 : 1;
            int doorX = center + (doorSide == Direction.EAST ? max - center : doorSide == Direction.WEST ? 1 - center : 0);
            int doorZ = center + (doorSide == Direction.SOUTH ? max - center : doorSide == Direction.NORTH ? 1 - center : 0);
            Block door = bossDoor(type);
            BlockState lower = door.defaultBlockState().setValue(LockedDoorBlock.FACING, doorSide)
                    .setValue(LockedDoorBlock.HALF, DoubleBlockHalf.LOWER)
                    .setValue(LockedDoorBlock.OPEN, false).setValue(LockedDoorBlock.UNLOCKED, false);
            set(doorX, doorY, doorZ, lower);
            set(doorX, doorY + 1, doorZ, lower.setValue(LockedDoorBlock.HALF, DoubleBlockHalf.UPPER));

            Block core = type == DungeonType.DESERT || type == DungeonType.WATER
                    ? ZSSRegistries.DUNGEON_CORE_SANDSTONE.get() : ZSSRegistries.DUNGEON_CORE_STONE.get();
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", ZeldaSwordSkills_Remastered.MOD_ID + ":dungeon_core");
            nbt.putString("dungeon_id", type.id().toString());
            nbt.putBoolean("completed", false);
            nbt.putInt("arena_radius", roomSize / 2);
            nbt.putInt("arena_width", roomSize);
            nbt.putInt("arena_height", roomHeight);
            nbt.putInt("door_offset_y", submerged ? 2 : 1);
            nbt.putInt("door_distance", Math.abs(doorX - center) + Math.abs(doorZ - center));
            nbt.putString("door_side", doorSide.getName());
            set(center, 0, center, core.defaultBlockState().setValue(DungeonBlocks.Core.SEALED, true), nbt);
        }

        private void placeCenterPiece() {
            int platformY = 1;
            if (submerged && !ocean) {
                fill(center - 1, 1, center - 1, center + 2, 1, center + 2, material);
                platformY = 2;
            }
            if (!ocean) fill(center - 1, platformY, center - 1, center + 2, platformY, center + 2, slab);
            set(center, platformY, center, type == DungeonType.ICE ? Blocks.QUARTZ_BLOCK.defaultBlockState() : material);
            int rewardY = platformY + 1;

            switch (type) {
                case DESERT, EARTH, WATER -> placeVanillaChest(center, rewardY, center);
                case FOREST -> {
                    if (variant % 5 == 0) placeMasterSwordPedestal(center, rewardY, center);
                    else placeVanillaChest(center, rewardY, center);
                }
                case FIRE -> set(center, rewardY, center, ZSSRegistries.SACRED_FLAME_DIN.get().defaultBlockState(),
                        blockEntity("sacred_flame_din"));
                case END -> set(center, rewardY, center, ZSSRegistries.SACRED_FLAME_NAYRU.get().defaultBlockState(),
                        blockEntity("sacred_flame_nayru"));
                case ICE -> { }
            }

            int hx = center + doorSide.getStepX();
            int hz = center + doorSide.getStepZ();
            set(hx, rewardY, hz, shell);
            if (type == DungeonType.FIRE || type == DungeonType.FOREST || type == DungeonType.ICE || type == DungeonType.END) {
                int sideX = doorSide.getAxis() == Direction.Axis.X ? center : 2 + random.nextInt(roomSize - 2);
                int sideZ = doorSide.getAxis() == Direction.Axis.Z ? center : 2 + random.nextInt(roomSize - 2);
                placeLockedChest(sideX, submerged && !ocean ? 2 : 1, sideZ, true);
            }
        }

        private void placePillars() {
            if (type == DungeonType.DESERT || type == DungeonType.END || type == DungeonType.EARTH
                    || random.nextFloat() >= (MIN_ROOM_SIZE + variant % 5) * 0.06F) return;
            int offset = roomSize < 11 ? 2 : 3;
            int lo = roomSize < 11 ? center - offset : 1 + offset;
            int hi = roomSize < 11 ? center + offset : max - offset;
            for (int y = 1; y < roomHeight - 1; y++) {
                set(lo, y, lo, material); set(lo, y, hi, material);
                set(hi, y, lo, material); set(hi, y, hi, material);
            }
            if (roomSize > 10 && random.nextBoolean()) {
                int y = roomHeight / 2;
                set(lo + 1, y, lo, material); set(lo, y, lo + 1, material);
                set(hi - 1, y, lo, material); set(hi, y, lo + 1, material);
                set(lo + 1, y, hi, material); set(lo, y, hi - 1, material);
                set(hi - 1, y, hi, material); set(hi, y, hi - 1, material);
            }
        }

        private void placeChandelier() {
            if (roomHeight <= 7) return;
            int y = roomHeight - 2;
            if (type == DungeonType.WATER) {
                set(center, y, center, Blocks.GLOWSTONE.defaultBlockState());
                for (int dx : new int[]{-1, 1}) for (int dz : new int[]{-1, 1})
                    set(center + dx, y, center + dz, Blocks.GLOWSTONE.defaultBlockState());
            } else if (type == DungeonType.END) {
                for (int x : new int[]{2, max - 1}) for (int z : new int[]{2, max - 1})
                    set(x, y, z, Blocks.GLOWSTONE.defaultBlockState());
            } else {
                set(center, y, center, Blocks.OAK_FENCE.defaultBlockState());
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) > 0) set(center + dx, y, center + dz, Blocks.OAK_FENCE.defaultBlockState());
                    if (Math.abs(dx) == 1 && Math.abs(dz) == 1)
                        set(center + dx, y - 1, center + dz, Blocks.GLOWSTONE.defaultBlockState());
                }
                set(center, y - 1, center, Blocks.OAK_FENCE.defaultBlockState());
                set(center, y - 2, center, Blocks.GLOWSTONE.defaultBlockState());
            }
        }

        private void placeLedge() {
            if (type == DungeonType.WATER || type == DungeonType.EARTH || MIN_ROOM_HEIGHT + variant / 5 <= 7
                    || random.nextFloat() >= (type == DungeonType.FIRE ? 0.75F : 0.5F)) return;
            int y = roomHeight / 2;
            for (int x = 2; x < max; x++) { set(x, y, 2, material); set(x, y, max - 1, material); }
            for (int z = 3; z < max - 1; z++) { set(2, y, z, material); set(max - 1, y, z, material); }
        }

        private void placeParapet() {
            BlockState north = stairs.setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.TOP);
            BlockState south = stairs.setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.TOP);
            BlockState west = stairs.setValue(StairBlock.FACING, Direction.EAST).setValue(StairBlock.HALF, Half.TOP);
            BlockState east = stairs.setValue(StairBlock.FACING, Direction.WEST).setValue(StairBlock.HALF, Half.TOP);
            BlockState exterior = type == DungeonType.FIRE ? shell : material;
            if (type == DungeonType.FIRE) north = south = west = east = shell;
            for (int x = 0; x < totalSize; x++) {
                set(x, roomHeight - 1, 0, north); set(x, roomHeight, 0, exterior);
                set(x, roomHeight - 1, totalSize - 1, south); set(x, roomHeight, totalSize - 1, exterior);
                if ((x & 1) == 0) { set(x, roomHeight + 1, 0, exterior); set(x, roomHeight + 1, totalSize - 1, exterior); }
            }
            for (int z = 0; z < totalSize; z++) {
                set(0, roomHeight - 1, z, west); set(0, roomHeight, z, exterior);
                set(totalSize - 1, roomHeight - 1, z, east); set(totalSize - 1, roomHeight, z, exterior);
                if ((z & 1) == 0) { set(0, roomHeight + 1, z, exterior); set(totalSize - 1, roomHeight + 1, z, exterior); }
            }
        }

        private void placeWindows() {
            if (random.nextFloat() >= (MIN_ROOM_SIZE + variant % 5) * 0.06F) return;
            int interval = (roomSize & 1) == 1 ? 2 : 3;
            int y = roomHeight / 2 + 1;
            BlockState window = random.nextFloat() < 0.25F ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState();
            boolean vines = type == DungeonType.FOREST || type == DungeonType.END;
            for (int i = 2; i < max; i++) {
                if (i % interval != 0) continue;
                set(i, y, 1, window); set(i, y, max, window);
                if (vines) {
                    placeVine(i, y - 1, 0, Direction.SOUTH);
                    placeVine(i, y - 1, totalSize - 1, Direction.NORTH);
                }
                set(1, y, i, window); set(max, y, i, window);
                if (vines) {
                    placeVine(0, y - 1, i, Direction.EAST);
                    placeVine(totalSize - 1, y - 1, i, Direction.WEST);
                }
            }
        }

        private void placeVine(int x, int y, int z, Direction attachment) {
            if (!random.nextBoolean()) return;
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(switch (attachment) {
                case NORTH -> VineBlock.NORTH; case SOUTH -> VineBlock.SOUTH;
                case EAST -> VineBlock.EAST; case WEST -> VineBlock.WEST;
                default -> VineBlock.UP;
            }, true);
            int length = 1 + random.nextInt(5);
            for (int i = 0; i < length && y - i > 0; i++) set(x, y - i, z, vine);
        }

        private void placeChestsAndJars() {
            if (variant % 10 == 0) placeVanillaChest(random.nextBoolean() ? 1 : max, roomHeight,
                    random.nextBoolean() ? 1 : max);
            if (variant % 7 == 0) {
                int x = 2 + random.nextInt(roomSize - 2);
                int z = 2 + random.nextInt(roomSize - 2);
                int y = random.nextBoolean() ? (submerged && !ocean ? 2 : 1) : roomHeight;
                if (isReplaceable(x, y, z)) placeInvisibleChest(x, y, z);
            }
            int floorJars = random.nextInt(5);
            int roofJars = random.nextInt(5) + 3;
            for (int i = 0; i < floorJars; i++) placeJar(false);
            for (int i = 0; i < roofJars; i++) placeJar(true);
        }

        /** Adds varied, deterministic obstacle silhouettes around the room perimeter. */
        private void placeTerrain() {
            int baseY = submerged && !ocean ? 2 : 1;
            int offset = Math.max(5, roomSize / 3);
            int[][] anchors = {
                    {center - offset, center - offset}, {center + offset, center + offset},
                    {center - offset, center + offset}, {center + offset, center - offset},
                    {center, center + offset + 3}, {center - offset - 3, center},
                    {center + offset + 3, center}, {center, center - offset - 3},
                    {center - offset - 2, center + offset + 2}, {center + offset + 2, center - offset - 2}
            };
            Block[] palette = terrainPalette();
            int count = 2 + Math.floorMod(variant, 3);
            placeNoisyGround(baseY, palette);
            for (int index = 0; index < count; index++) {
                int x = anchors[index][0];
                int z = anchors[index][1];
                int radius = 1 + terrainNoise(x, z, index) % 2;
                int height = 1 + terrainNoise(z, x, index + 7) % 3;
                int shape = Math.floorMod(variant + index, 4);
                for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                    int distance = Math.max(Math.abs(dx), Math.abs(dz));
                    int irregular = terrainNoise(x + dx, z + dz, index + 13) % 3;
                    if (shape == 0 && distance == radius && irregular == 0) continue;
                    if (shape == 1 && Math.abs(dx) + Math.abs(dz) > radius + 1) continue;
                    if (shape == 2 && distance > radius - (Math.abs(dx + dz) % 2)) continue;
                    int columnHeight = Math.max(1, height - distance / 2 + irregular - 1);
                    for (int dy = 0; dy < columnHeight; dy++) {
                        Block block = (dy == columnHeight - 1 && (terrainNoise(dx, dz, index) & 1) == 0)
                                ? terrainTopBlock(palette, x + dx, z + dz, index + 17)
                                : shell.getBlock();
                        setTerrain(x + dx, baseY + dy, z + dz, block.defaultBlockState());
                    }
                }
            }
        }

        private void placeNoisyGround(int baseY, Block[] palette) {
            int exclusion = Math.max(5, roomSize / 7);
            for (int x = 2; x < max; x++) for (int z = 2; z < max; z++) {
                if (Math.max(Math.abs(x - center), Math.abs(z - center)) <= exclusion) continue;
                // Low-frequency samples create connected natural-looking rises instead of a checkerboard.
                int sample = terrainNoise(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 101);
                int detail = terrainNoise(x, z, 109) % 5;
                boolean island = ocean ? sample % 100 < 18 : sample % 100 < 30;
                if (!island || detail == 0) continue;
                int height = sample % 3;
                if (height == 0) continue;
                for (int y = 0; y < height; y++) {
                    Block block = y == height - 1 && detail == 1
                            ? terrainTopBlock(palette, x, z, 127) : shell.getBlock();
                    setTerrain(x, baseY + y, z, block.defaultBlockState());
                }
            }
        }

        private Block terrainTopBlock(Block[] palette, int x, int z, int salt) {
            // A sparse light source is mixed into hill tops without turning the room into a lamp field.
            if (type != DungeonType.FOREST && terrainNoise(x, z, salt) % 24 == 0) return Blocks.GLOWSTONE;
            return palette[1 + terrainNoise(x, z, salt + 1) % (palette.length - 1)];
        }

        private int terrainNoise(int x, int z, int salt) {
            long value = 0x9E3779B97F4A7C15L ^ (long) type.ordinal() * 0x632BE59BD9B4E019L
                    ^ (long) variant * 0x85157AF5L ^ (long) (x * 73428767) ^ (long) (z * 912931L)
                    ^ (long) salt * 0x27D4EB2DL;
            value ^= value >>> 30;
            value *= 0xBF58476D1CE4E5B9L;
            value ^= value >>> 27;
            return (int) (value ^ (value >>> 31)) & 0x7FFFFFFF;
        }

        private void setTerrain(int x, int y, int z, BlockState state) {
            if (x <= 1 || x >= max || z <= 1 || z >= max) return;
            Placed existing = blocks.get(new BlockPos(x, y, z));
            if (existing != null && existing.nbt() != null) return;
            set(x, y, z, state);
        }

        private Block[] terrainPalette() {
            return switch (type) {
                case DESERT -> new Block[]{shell.getBlock(), Blocks.SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE};
                case EARTH -> new Block[]{shell.getBlock(), Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS};
                case FIRE -> new Block[]{shell.getBlock(), Blocks.OBSIDIAN, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.POLISHED_BLACKSTONE};
                case FOREST -> new Block[]{shell.getBlock(), Blocks.MOSS_BLOCK, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE};
                case ICE -> new Block[]{shell.getBlock(), Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK, Blocks.QUARTZ_BLOCK};
                case WATER -> new Block[]{shell.getBlock(), Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE};
                case END -> new Block[]{shell.getBlock(), Blocks.QUARTZ_BLOCK, Blocks.CALCITE, Blocks.SMOOTH_QUARTZ};
            };
        }

        private void placeJar(boolean roof) {
            int x = 1 + random.nextInt(roomSize);
            int z = 1 + random.nextInt(roomSize);
            int y = roof ? roomHeight : submerged && !ocean ? 2 : 1;
            if (isReplaceable(x, y, z)) set(x, y, z, ZSSRegistries.CERAMIC_JAR.get().defaultBlockState(), storageNbt(DungeonLootTables.JAR));
        }

        private boolean isReplaceable(int x, int y, int z) {
            Placed placed = blocks.get(new BlockPos(x, y, z));
            return placed == null || placed.state().isAir() || !placed.state().getFluidState().isEmpty();
        }

        private void placeVanillaChest(int x, int y, int z) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", "minecraft:chest");
            nbt.putString("LootTable", DungeonLootTables.temple(type, x == center && z == center && y < roomHeight).toString());
            set(x, y, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, doorSide.getOpposite()), nbt);
        }

        private void placeLockedChest(int x, int y, int z, boolean visible) {
            BlockState state = ZSSRegistries.CHEST_LOCKED.get().defaultBlockState()
                    .setValue(LockedChestBlock.FACING, doorSide.getOpposite())
                    .setValue(LockedChestBlock.UNLOCKED, false).setValue(LockedChestBlock.VISIBLE, visible);
            set(x, y, z, state, storageNbt(DungeonLootTables.temple(type, true)));
        }

        private void placeInvisibleChest(int x, int y, int z) {
            BlockState state = ZSSRegistries.CHEST_INVISIBLE.get().defaultBlockState()
                    .setValue(LockedChestBlock.FACING, doorSide.getOpposite())
                    .setValue(LockedChestBlock.UNLOCKED, false).setValue(LockedChestBlock.VISIBLE, false);
            set(x, y, z, state, storageNbt(DungeonLootTables.temple(type, false)));
        }

        private void placeMasterSwordPedestal(int x, int y, int z) {
            BlockState state = ZSSRegistries.PEDESTAL.get().defaultBlockState()
                    .setValue(PedestalBlock.PENDANTS, 0).setValue(PedestalBlock.UNLOCKED, false)
                    .setValue(PedestalBlock.HAS_SWORD, true);
            CompoundTag nbt = blockEntity("pedestal");
            nbt.putBoolean("has_sword", true);
            nbt.putByte("orientation", (byte) 0);
            CompoundTag sword = new ItemStack(ZSSRegistries.MASTER_SWORD.get()).save(new CompoundTag());
            nbt.put("sword", sword);
            CompoundTag inventory = new CompoundTag();
            inventory.putInt("Size", 3);
            inventory.put("Items", new ListTag());
            nbt.put("inventory", inventory);
            set(x, y, z, state, nbt);
        }

        private CompoundTag storageNbt(ResourceLocation lootTable) {
            CompoundTag nbt = blockEntity("storage");
            nbt.putString("LootTable", lootTable.toString());
            return nbt;
        }

        private CompoundTag blockEntity(String id) {
            CompoundTag nbt = new CompoundTag();
            nbt.putString("id", ZeldaSwordSkills_Remastered.MOD_ID + ":" + id);
            return nbt;
        }

        private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
            for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) for (int x = x1; x <= x2; x++)
                set(x, y, z, state);
        }

        private void set(int x, int y, int z, BlockState state) {
            set(x, y, z, state, null);
        }

        private void set(int x, int y, int z, BlockState state, CompoundTag nbt) {
            blocks.put(new BlockPos(x, y, z), new Placed(state, nbt == null ? null : nbt.copy()));
        }

        private CompoundTag structureTag() {
            CompoundTag root = new CompoundTag();
            root.put("size", ints(totalSize, roomHeight + 2, totalSize));
            root.put("entities", new ListTag());
            List<PlacedAt> ordered = blocks.entrySet().stream()
                    .map(entry -> new PlacedAt(entry.getKey(), entry.getValue()))
                    .sorted(Comparator.comparingInt((PlacedAt block) -> block.pos().getY())
                            .thenComparingInt(block -> block.pos().getZ()).thenComparingInt(block -> block.pos().getX()))
                    .toList();
            Map<BlockState, Integer> paletteIds = new LinkedHashMap<>();
            ListTag blockList = new ListTag();
            for (PlacedAt placed : ordered) {
                int state = paletteIds.computeIfAbsent(placed.placed().state(), ignored -> paletteIds.size());
                CompoundTag block = new CompoundTag();
                block.put("pos", ints(placed.pos().getX(), placed.pos().getY(), placed.pos().getZ()));
                block.putInt("state", state);
                if (placed.placed().nbt() != null) block.put("nbt", placed.placed().nbt().copy());
                blockList.add(block);
            }
            ListTag palette = new ListTag();
            paletteIds.keySet().forEach(state -> palette.add(NbtUtils.writeBlockState(state)));
            root.put("palette", palette);
            root.put("blocks", blockList);
            return NbtUtils.addCurrentDataVersion(root);
        }

        private record PlacedAt(BlockPos pos, Placed placed) {
        }
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }

    private static Block secretStone(DungeonType type) {
        return switch (type) {
            case DESERT, WATER -> ZSSRegistries.SECRET_STONE_SANDSTONE.get();
            case EARTH -> ZSSRegistries.SECRET_STONE_STONE_BRICKS.get();
            case FIRE -> ZSSRegistries.SECRET_STONE_NETHER_WART_BLOCK.get();
            case FOREST, END -> ZSSRegistries.SECRET_STONE_MOSSY_COBBLESTONE.get();
            case ICE -> ZSSRegistries.SECRET_STONE_ICE.get();
        };
    }

    private static Block material(DungeonType type) {
        return switch (type) {
            case DESERT, WATER -> Blocks.SANDSTONE;
            case EARTH -> Blocks.STONE_BRICKS;
            case FIRE -> Blocks.END_STONE;
            case FOREST, END -> Blocks.MOSSY_COBBLESTONE;
            case ICE -> Blocks.BLUE_ICE;
        };
    }

    private static SlabBlock slab(DungeonType type) {
        return switch (type) {
            case DESERT, WATER -> (SlabBlock) Blocks.SANDSTONE_SLAB;
            case EARTH -> (SlabBlock) Blocks.STONE_BRICK_SLAB;
            case FIRE, ICE -> (SlabBlock) Blocks.QUARTZ_SLAB;
            case FOREST, END -> (SlabBlock) Blocks.MOSSY_COBBLESTONE_SLAB;
        };
    }

    private static StairBlock stairs(DungeonType type) {
        return switch (type) {
            case DESERT, WATER -> (StairBlock) Blocks.SANDSTONE_STAIRS;
            case EARTH -> (StairBlock) Blocks.STONE_BRICK_STAIRS;
            case FIRE, ICE -> (StairBlock) Blocks.QUARTZ_STAIRS;
            case FOREST, END -> (StairBlock) Blocks.COBBLESTONE_STAIRS;
        };
    }

    private static Block bossDoor(DungeonType type) {
        return switch (type) {
            case DESERT -> ZSSRegistries.DOOR_BOSS_DESERT.get();
            case EARTH -> ZSSRegistries.DOOR_BOSS_EARTH.get();
            case FIRE -> ZSSRegistries.DOOR_BOSS_FIRE.get();
            case FOREST -> ZSSRegistries.DOOR_BOSS_FOREST.get();
            case ICE -> ZSSRegistries.DOOR_BOSS_ICE.get();
            case WATER -> ZSSRegistries.DOOR_BOSS_WATER.get();
            case END -> ZSSRegistries.DOOR_BOSS_END.get();
        };
    }
}
