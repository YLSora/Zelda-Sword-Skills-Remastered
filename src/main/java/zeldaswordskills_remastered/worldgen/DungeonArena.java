package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import zeldaswordskills_remastered.block.entity.StageNineBlockEntities.DungeonCore;

/** Exact template coordinates and collision-safe placement for rotated boss rooms. */
public final class DungeonArena {
    private DungeonArena() {}

    public static boolean validRoom(DungeonCore core) {
        return core.arenaWidth() >= 31 && core.arenaWidth() <= 43
                && core.arenaHeight() >= 16 && core.arenaHeight() <= 22
                && core.doorSide().isPresent() && core.doorDistance() > 0;
    }

    public static BlockPos minimum(DungeonCore core) {
        BlockPos first = localPos(core, 0, 0, 0);
        BlockPos last = localPos(core, core.arenaWidth() - 1, 0, core.arenaWidth() - 1);
        return new BlockPos(Math.min(first.getX(), last.getX()), first.getY(), Math.min(first.getZ(), last.getZ()));
    }

    public static BlockPos localPos(DungeonCore core, int x, int y, int z) {
        return core.getBlockPos().offset(new BlockPos(x - core.arenaWidth() / 2, y, z - core.arenaWidth() / 2)
                .rotate(core.structureRotation()));
    }

    public static AABB interior(DungeonCore core) {
        BlockPos min = minimum(core);
        return new AABB(min.offset(1, 1, 1), min.offset(core.arenaWidth() - 1,
                core.arenaHeight() - 1, core.arenaWidth() - 1));
    }

    public static boolean contains(DungeonCore core, AABB box) {
        AABB room = interior(core);
        return box.minX >= room.minX && box.maxX <= room.maxX && box.minY >= room.minY && box.maxY <= room.maxY
                && box.minZ >= room.minZ && box.maxZ <= room.maxZ;
    }

    public static BlockPos spawnPos(DungeonCore core, int corner) {
        int far = core.arenaWidth() - 3;
        return localPos(core, corner < 2 ? 2 : far, 3, (corner & 1) == 0 ? 2 : far);
    }

    /** Returns a center-aligned slot for the initial dungeon bosses. */
    public static BlockPos bossSpawnPos(DungeonCore core, int index, int count) {
        int offset = Math.max(3, Math.min(4, core.arenaWidth() / 8));
        int dx;
        int dz;
        if (count <= 1) {
            dx = dz = 0;
        } else if (count == 2) {
            dx = index == 0 ? -offset : offset;
            dz = 0;
        } else {
            dx = (index & 1) == 0 ? -offset : offset;
            dz = (index < 2) ? -offset : offset;
        }
        int center = core.arenaWidth() / 2;
        return localPos(core, center + dx, 3, center + dz);
    }

    public static boolean positionAtBossSlot(ServerLevel level, DungeonCore core, Entity entity,
                                              int index, int count) {
        BlockPos base = bossSpawnPos(core, index, count);
        for (double dx : new double[]{0, -.5, .5, -1, 1, -1.5, 1.5})
            for (double dz : new double[]{0, -.5, .5, -1, 1, -1.5, 1.5}) {
                entity.setPos(base.getX() + dx + .5, base.getY(), base.getZ() + dz + .5);
                if (contains(core, entity.getBoundingBox()) && level.noCollision(entity)) return true;
            }
        return false;
    }

    public static boolean positionAtCorner(ServerLevel level, DungeonCore core, Entity entity, int corner) {
        BlockPos base = spawnPos(core, corner);
        // Search beside the intended corner pillar, never place an entity inside the room's masonry.
        for (int dy = 0; dy < core.arenaHeight() - 2; dy++) {
            int y = 1 + Math.floorMod(2 + dy, core.arenaHeight() - 2);
            for (double dx : new double[]{0, -.5, .5, -1, 1, -1.5, 1.5, -2, 2})
                for (double dz : new double[]{0, -.5, .5, -1, 1, -1.5, 1.5, -2, 2}) {
                entity.setPos(base.getX() + dx + .5, core.getBlockPos().getY() + y, base.getZ() + dz + .5);
                AABB box = entity.getBoundingBox();
                if (contains(core, box) && level.noCollision(entity)) return true;
            }
        }
        return false;
    }

}
