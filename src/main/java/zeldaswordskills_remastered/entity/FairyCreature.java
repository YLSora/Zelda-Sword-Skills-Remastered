package zeldaswordskills_remastered.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Floating ambient fairy that remains catchable by a Fairy Bottle. */
public final class FairyCreature extends LegacyCreature {
    public FairyCreature(EntityType<? extends LegacyCreature> type, Level level, Kind kind) {
        super(type, level, kind);
        moveControl = new FlyingMoveControl(this, 12, true);
        setNoGravity(true);
    }

    @Override protected void registerGoals() {
        goalSelector.addGoal(1, new FloatWanderGoal(this));
        goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    @Override public boolean isNoGravity() { return true; }
    @Override public int getMaxSpawnClusterSize() { return 1; }

    private static final class FloatWanderGoal extends Goal {
        private final FairyCreature fairy;
        private int nextMove;

        private FloatWanderGoal(FairyCreature fairy) {
            this.fairy = fairy;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override public boolean canUse() { return fairy.tickCount >= nextMove; }

        @Override public void start() {
            nextMove = fairy.tickCount + 20 + fairy.getRandom().nextInt(40);
            Vec3 origin = fairy.position();
            fairy.getMoveControl().setWantedPosition(origin.x + (fairy.getRandom().nextDouble() - .5D) * 5.0D,
                    origin.y + (fairy.getRandom().nextDouble() - .5D) * 3.0D,
                    origin.z + (fairy.getRandom().nextDouble() - .5D) * 5.0D, .45D);
        }
    }
}
