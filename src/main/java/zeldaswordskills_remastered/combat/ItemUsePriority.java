package zeldaswordskills_remastered.combat;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/** Detects use handlers without executing an item action or consulting its cooldown. */
public final class ItemUsePriority {
    private static final List<Method> USE_METHODS = List.of(
            ObfuscationReflectionHelper.findMethod(Item.class, "m_7203_", Level.class, Player.class, InteractionHand.class),
            ObfuscationReflectionHelper.findMethod(Item.class, "m_6225_", UseOnContext.class),
            ObfuscationReflectionHelper.findMethod(Item.class, "m_6880_", ItemStack.class, Player.class,
                    LivingEntity.class, InteractionHand.class));
    private static final ClassValue<Boolean> HAS_USE_HANDLER = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                for (Method method : USE_METHODS) {
                    if (type.getMethod(method.getName(), method.getParameterTypes()).getDeclaringClass() != Item.class)
                        return true;
                }
                return false;
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException("Missing inherited Item use method: " + type.getName(), exception);
            }
        }
    };

    private ItemUsePriority() {}

    public static boolean takesPriority(Player player) {
        return player.isUsingItem() || hasUseAction(player.getMainHandItem()) || hasUseAction(player.getOffhandItem());
    }

    private static boolean hasUseAction(ItemStack stack) {
        return !stack.isEmpty() && (stack.isEdible() || stack.getUseDuration() > 0
                || stack.canPerformAction(ToolActions.SHIELD_BLOCK) || HAS_USE_HANDLER.get(stack.getItem().getClass()));
    }
}
