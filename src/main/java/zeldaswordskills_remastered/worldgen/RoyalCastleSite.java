package zeldaswordskills_remastered.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** Immutable, persisted location shared by structure generation and protection. */
public record RoyalCastleSite(int x, int y, int z) {
    public static final int SIZE = 216;
    public static final int HEIGHT = 96;
    public static final int BLEND = 16;
    public static final int CLEARANCE = 100;

    public BlockPos origin() { return new BlockPos(x, y, z); }
    public ChunkPos startChunk() { return new ChunkPos((x + 112) >> 4, (z + 112) >> 4); }

    public BoundingBox bounds() {
        return new BoundingBox(x - BLEND, y - 32, z - BLEND,
                x + SIZE + BLEND - 1, y + HEIGHT - 1, z + SIZE + BLEND - 1);
    }

    public boolean contains(int px, int pz) {
        return px >= x && px < x + SIZE && pz >= z && pz < z + SIZE;
    }

    public boolean excludes(BoundingBox other) {
        // Horizontal distance also excludes underground structures below the estate.
        int dx = Math.max(0, Math.max(x - BLEND - other.maxX(), other.minX() - (x + SIZE + BLEND - 1)));
        int dz = Math.max(0, Math.max(z - BLEND - other.maxZ(), other.minZ() - (z + SIZE + BLEND - 1)));
        return (long) dx * dx + (long) dz * dz <= CLEARANCE * CLEARANCE;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", x);
        tag.putInt("Y", y);
        tag.putInt("Z", z);
        return tag;
    }

    public static RoyalCastleSite load(CompoundTag tag) {
        return new RoyalCastleSite(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }
}
