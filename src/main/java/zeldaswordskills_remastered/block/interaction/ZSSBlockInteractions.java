package zeldaswordskills_remastered.block.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Stable server-side boundary between stage-nine blocks and later tools/entities.
 * Implementations own all mutations; callers only describe the validated action.
 */
public final class ZSSBlockInteractions {
    private ZSSBlockInteractions() {}

    public enum Weight {
        VERY_LIGHT, LIGHT, MEDIUM, HEAVY, VERY_HEAVY, IMPOSSIBLE;

        public boolean allows(Weight required) {
            return this != IMPOSSIBLE && ordinal() >= required.ordinal();
        }
    }

    public enum ExplosionKind { NORMAL, BOMB, FIRE_BOMB, WATER_BOMB, BOMB_FLOWER }
    public enum SacredFlame { DIN, FARORE, NAYRU }

    public interface Hookable {
        boolean canHook(BlockState state, Direction hitFace);
        default boolean hookBreaks(BlockState state, Direction hitFace) { return false; }
    }

    public interface Liftable {
        Weight liftWeight(BlockState state);
        default void onLifted(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, ItemStack carrier) {}
    }

    public interface Smashable {
        Weight smashWeight(BlockState state);
        InteractionResult smash(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player,
                                ItemStack tool, Weight strength, Direction face);
    }

    public interface QuakeReactive {
        boolean onQuake(ServerLevel level, BlockPos pos, BlockState state, @Nullable ServerPlayer source);
    }

    public interface Explodable {
        boolean onZssExplosion(ServerLevel level, BlockPos pos, BlockState state, ExplosionKind kind,
                               @Nullable ServerPlayer source);
    }

    public interface WhipActivatable {
        boolean activateByWhip(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player, int whipLevel);
    }

    /** Implement this on later-stage items which can consume a sacred flame directly. */
    public interface SacredFlameReceiver {
        boolean receiveFlame(ItemStack stack, ServerLevel level, ServerPlayer player, SacredFlame flame);
    }

    /** Implement this on later-stage dungeon controllers that own a block position. */
    public interface DungeonLinked {
        ResourceLocation dungeonId();
    }

    public static boolean canHook(BlockState state, Direction face) {
        return state.getBlock() instanceof Hookable hookable && hookable.canHook(state, face);
    }

    public static boolean quake(ServerLevel level, BlockPos pos, @Nullable ServerPlayer source) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof QuakeReactive reactive && reactive.onQuake(level, pos, state, source);
    }

    public static boolean explode(ServerLevel level, BlockPos pos, ExplosionKind kind, @Nullable ServerPlayer source) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof Explodable explodable && explodable.onZssExplosion(level, pos, state, kind, source);
    }

    public static InteractionResult smash(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack tool,
                                          Weight strength, Direction face) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof Smashable smashable
                ? smashable.smash(level, pos, state, player, tool, strength, face) : InteractionResult.PASS;
    }

    public static boolean activateWhip(ServerLevel level, BlockPos pos, ServerPlayer player, int whipLevel) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof WhipActivatable activatable
                && activatable.activateByWhip(level, pos, state, player, Math.max(0, whipLevel));
    }
}
