package zeldaswordskills_remastered.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.Vec3;

/** Forest's golden Skulltula, with fixed boss attributes and normal Skulltula defenses. */
public final class ForestBossCreature extends SkulltulaCreature {
    public ForestBossCreature(EntityType<? extends LegacyCreature> type, Level level) {
        super(type, level, Kind.FOREST_BOSS);
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        // The forest encounter uses two giant golden Skulltulas; keep their hitbox in
        // sync with the four-times client model scale.
        return kind() == Kind.FOREST_BOSS ? super.getDimensions(pose).scale(4.0F) : super.getDimensions(pose);
    }

    @Override public void makeStuckInBlock(BlockState state, Vec3 multiplier) {
        if (!state.is(Blocks.COBWEB)) super.makeStuckInBlock(state, multiplier);
    }
}
