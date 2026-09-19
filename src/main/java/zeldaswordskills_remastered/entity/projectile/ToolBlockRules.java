package zeldaswordskills_remastered.entity.projectile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import zeldaswordskills_remastered.block.interaction.ZSSBlockInteractions;

/** Material categories are data-driven; mechanism callbacks retain face-specific authority. */
final class ToolBlockRules {
    private static final TagKey<Block> WOOD = tag("hookable_wood");
    private static final TagKey<Block> STONE = tag("hookable_stone");
    private static final TagKey<Block> MULTI = tag("hookable_multi");
    private static final TagKey<Block> GLASS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("forge", "glass"));
    private static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("forge", "glass_panes"));
    private static final TagKey<Block> WHIP = tag("magic_whip_attachable");

    private ToolBlockRules() { }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("zeldaswordskills_remastered", path));
    }

    static boolean canHook(BlockState state, Direction face, boolean stone, boolean multi) {
        if (state.getBlock() instanceof ZSSBlockInteractions.Hookable)
            return ZSSBlockInteractions.canHook(state, face);
        return state.is(multi ? MULTI : stone ? STONE : WOOD);
    }

    static boolean hookBreaks(BlockState state, boolean stone, boolean multi) {
        return state.is(GLASS) || state.is(GLASS_PANES) || stone && !multi && state.is(WOOD);
    }

    static boolean canAttachWhip(Level level, BlockPos pos, BlockState state) {
        return state.is(WHIP) || !state.getCollisionShape(level, pos).isEmpty()
                && state.getDestroySpeed(level, pos) > 1.0F;
    }
}
