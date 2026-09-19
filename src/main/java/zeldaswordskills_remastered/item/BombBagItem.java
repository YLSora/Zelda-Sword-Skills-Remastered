package zeldaswordskills_remastered.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;
import java.util.List;
import zeldaswordskills_remastered.entity.projectile.ThrownBomb;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import zeldaswordskills_remastered.registry.ZSSRegistries;

/** A three-compartment bomb inventory with a shared, upgradeable capacity. */
public final class BombBagItem extends Item {
    public static final int BASE_CAPACITY = 10;
    public static final int MAX_CAPACITY = 50;
    public static final String SELECTED_TAG = "selected_type";
    public static final String[] TYPES = { "standard_bomb", "water_bomb", "fire_bomb" };

    public BombBagItem(Properties properties) { super(properties); }

    public static boolean cycleSelected(Player player) {
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof BombBagItem)) return false;
        if (getCount(stack, "standard_bomb") + getCount(stack, "water_bomb") + getCount(stack, "fire_bomb") == 0) {
            player.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.bomb_bag_empty"), true);
            return true;
        }
        cycleSelected(stack);
        player.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.bomb_bag_selected",
                Component.translatable("item.zeldaswordskills_remastered." + selectedType(stack))), true);
        return true;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.sidedSuccess(bag, true);
        boolean changed = player.isShiftKeyDown() ? deposit(serverPlayer, bag) : throwBomb(serverPlayer, bag);
        if (!changed) return InteractionResultHolder.fail(bag);
        return InteractionResultHolder.consume(bag);
    }

    private static boolean deposit(ServerPlayer player, ItemStack bag) {
        CompoundTag counts = bag.getOrCreateTagElement("bombs");
        int total = total(counts);
        int before = total;
        if (total >= capacity(bag)) return false;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && total < capacity(bag); slot++) {
            ItemStack source = player.getInventory().getItem(slot);
            for (String type : TYPES) if (source.is(ZSSRegistries.getItem(type))) {
                int moved = Math.min(source.getCount(), capacity(bag) - total);
                source.shrink(moved); counts.putInt(type, counts.getInt(type) + moved); total += moved;
            }
        }
        return total > before;
    }

    private static boolean withdraw(ServerPlayer player, ItemStack bag) {
        CompoundTag counts = bag.getOrCreateTagElement("bombs");
        for (String type : TYPES) {
            int count = counts.getInt(type);
            if (count <= 0) continue;
            ItemStack bomb = new ItemStack(ZSSRegistries.getItem(type));
            if (!player.getInventory().add(bomb)) return false;
            counts.putInt(type, count - 1);
            return true;
        }
        player.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.bomb_bag_empty"), true);
        return false;
    }

    private static boolean throwBomb(ServerPlayer player, ItemStack bag) {
        String selected = selectedType(bag);
        CompoundTag counts = bag.getOrCreateTagElement("bombs");
        if (counts.getInt(selected) <= 0) {
            selected = firstType(bag);
            if (selected == null) { player.displayClientMessage(Component.translatable("message.zeldaswordskills_remastered.bomb_bag_empty"), true); return false; }
            bag.getOrCreateTag().putString(SELECTED_TAG, selected);
        }
        ThrownBomb.BombKind kind = switch (selected) { case "fire_bomb" -> ThrownBomb.BombKind.FIRE; case "water_bomb" -> ThrownBomb.BombKind.WATER; default -> ThrownBomb.BombKind.STANDARD; };
        counts.putInt(selected, counts.getInt(selected) - 1);
        ThrownBomb bomb = new ThrownBomb(player.level(), player, kind);
        bomb.setItem(new ItemStack(ZSSRegistries.getItem(selected)));
        bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.9F, 2.0F);
        player.level().addFreshEntity(bomb);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.7F, 0.7F);
        addSharedCooldown(player);
        return true;
    }

    public static void addSharedCooldown(Player player) {
        player.getCooldowns().addCooldown(ZSSRegistries.getItem("bomb_bag"), 10);
        for (String type : TYPES) player.getCooldowns().addCooldown(ZSSRegistries.getItem(type), 10);
    }

    private static int total(CompoundTag counts) { int total = 0; for (String type : TYPES) total += Math.max(0, counts.getInt(type)); return total; }

    public static int capacity(ItemStack bag) {
        return Math.min(MAX_CAPACITY, Math.max(BASE_CAPACITY, bag.getOrCreateTag().getInt("capacity") == 0
                ? BASE_CAPACITY : bag.getOrCreateTag().getInt("capacity")));
    }

    public static void setCapacity(ItemStack bag, int value) {
        bag.getOrCreateTag().putInt("capacity", Math.min(MAX_CAPACITY, Math.max(BASE_CAPACITY, value)));
    }

    public static String type(ItemStack bag) {
        CompoundTag counts = bag.getOrCreateTagElement("bombs");
        for (String type : TYPES) if (counts.getInt(type) > 0) return type;
        return "standard_bomb";
    }

    public static String selectedType(ItemStack bag) {
        String selected = bag.getOrCreateTag().getString(SELECTED_TAG);
        return isType(selected) ? selected : (firstType(bag) == null ? TYPES[0] : firstType(bag));
    }

    public static void cycleSelected(ItemStack bag) {
        String current = selectedType(bag);
        for (int i = 0; i < TYPES.length; i++) if (TYPES[i].equals(current)) {
            for (int step = 1; step <= TYPES.length; step++) {
                String next = TYPES[(i + step) % TYPES.length];
                if (getCount(bag, next) > 0) { bag.getOrCreateTag().putString(SELECTED_TAG, next); return; }
            }
        }
    }

    public static int getCount(ItemStack bag, String type) { return bag.getOrCreateTagElement("bombs").getInt(type); }
    private static String firstType(ItemStack bag) { for (String type : TYPES) if (getCount(bag, type) > 0) return type; return null; }
    private static boolean isType(String type) { for (String known : TYPES) if (known.equals(type)) return true; return false; }

    public static boolean canStore(ItemStack bag, ItemStack bomb) {
        for (String type : TYPES) if (bomb.is(ZSSRegistries.getItem(type))) {
            CompoundTag counts = bag.getOrCreateTagElement("bombs");
        return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.zeldaswordskills_remastered.bomb_bag.store").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.zeldaswordskills_remastered.bomb_bag.switch").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.zeldaswordskills_remastered.bomb_bag.selected", Component.translatable("item.zeldaswordskills_remastered." + selectedType(stack)))
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.zeldaswordskills_remastered.standard_bomb").append(Component.literal(": " + getCount(stack, "standard_bomb"))).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zeldaswordskills_remastered.water_bomb").append(Component.literal(": " + getCount(stack, "water_bomb"))).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.zeldaswordskills_remastered.fire_bomb").append(Component.literal(": " + getCount(stack, "fire_bomb"))).withStyle(ChatFormatting.RED));
    }

    public static boolean add(ItemStack bag, ItemStack bomb) {
        if (!canStore(bag, bomb)) return false;
        CompoundTag counts = bag.getOrCreateTagElement("bombs");
        int amount = Math.min(bomb.getCount(), capacity(bag) - total(counts));
        if (amount <= 0) return false;
        for (String type : TYPES) if (bomb.is(ZSSRegistries.getItem(type))) counts.putInt(type, counts.getInt(type) + amount);
        bomb.shrink(amount);
        return true;
    }

    public static boolean combine(ItemStack first, ItemStack second) {
        if (!first.is(ZSSRegistries.getItem("bomb_bag")) || !second.is(ZSSRegistries.getItem("bomb_bag"))
                || !canStore(first, new ItemStack(ZSSRegistries.getItem(type(second))))
                || capacity(first) + capacity(second) > MAX_CAPACITY) return false;
        CompoundTag target = first.getOrCreateTagElement("bombs");
        CompoundTag source = second.getOrCreateTagElement("bombs");
        setCapacity(first, capacity(first) + capacity(second));
        for (String type : TYPES) target.putInt(type, target.getInt(type) + source.getInt(type));
        return true;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack bag, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || player.level().isClientSide) return false;
        ItemStack other = slot.getItem();
        if (!other.isEmpty() && add(bag, other)) { slot.set(other); return true; }
        return false;
    }

    public boolean overrideOtherStackedOnMe(ItemStack bag, ItemStack other, Slot slot, ClickAction action, Player player) {
        if (action != ClickAction.PRIMARY || player.level().isClientSide || !other.isEmpty()) return false;
        CompoundTag counts = bag.getOrCreateTagElement("bombs");
        for (String type : TYPES) if (counts.getInt(type) > 0) {
            ItemStack result = new ItemStack(ZSSRegistries.getItem(type));
            counts.putInt(type, counts.getInt(type) - 1);
            if (!player.getInventory().add(result)) player.drop(result, false);
            return true;
        }
        return false;
    }
}
