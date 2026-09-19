package zeldaswordskills_remastered.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.PedestalBlock;
import zeldaswordskills_remastered.item.ZeldaCombatItems;
import zeldaswordskills_remastered.menu.PedestalMenu;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class PedestalBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOT_POWER = 0;
    public static final int SLOT_WISDOM = 1;
    public static final int SLOT_COURAGE = 2;
    public static final int SLOT_COUNT = 3;
    public static final String HAS_SWORD_TAG = "has_sword";
    public static final String SWORD_TAG = "sword";
    public static final String ORIENTATION_TAG = "orientation";

    private boolean droppingContents;
    private byte orientation;
    private ItemStack sword = ItemStack.EMPTY;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.is(expectedPendant(slot));
        }

        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return isUnlocked() ? stack : super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncBlockState();
        }
    };
    private LazyOptional<IItemHandler> itemHandler = LazyOptional.of(() -> inventory);

    public PedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ZSSRegistries.PEDESTAL_BLOCK_ENTITY.get(), pos, state);
    }

    public ItemStackHandler inventory() {
        return inventory;
    }

    public boolean hasSword() {
        return !sword.isEmpty();
    }

    public ItemStack sword() {
        return sword;
    }

    public byte orientation() {
        return orientation;
    }

    public boolean isUnlocked() {
        return level != null && getBlockState().hasProperty(PedestalBlock.UNLOCKED)
                && getBlockState().getValue(PedestalBlock.UNLOCKED);
    }

    public int getPowerLevel() {
        return sword.getItem() instanceof ZeldaCombatItems.Sword masterSword && masterSword.masterSword() ? 15 : 0;
    }

    public boolean setSword(ItemStack stack, @Nullable Player player) {
        if (!isUnlocked() || hasSword() || !isAllowedSword(stack)) return false;

        sword = copySwordForPedestal(stack);
        if (player != null) {
            Direction.Axis axis = Direction.fromYRot(player.getYRot()).getAxis();
            orientation = (byte) (axis == Direction.Axis.Z ? 0 : 1);
        }
        setChanged();
        syncBlockState();
        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, ZSSRegistries.SWORD_STRIKE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
            if (sword.is(ZSSRegistries.TRUE_MASTER_SWORD.get()) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                ZSSAdvancementService.swordProgress(serverPlayer, "sword.true");
        }
        return true;
    }

    public boolean retrieveSword() {
        if (level == null || level.isClientSide || sword.isEmpty()) return false;

        ItemStack retrieved = sword;
        sword = ItemStack.EMPTY;
        Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D,
                worldPosition.getZ() + 0.5D, retrieved);
        level.playSound(null, worldPosition, ZSSRegistries.SWORD_STRIKE.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
        setChanged();
        syncBlockState();
        return true;
    }

    public void changeOrientation() {
        if (!hasSword()) return;
        orientation = (byte) (orientation == 0 ? 1 : 0);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        droppingContents = true;
        try {
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                ItemStack stack = inventory.getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
                    inventory.setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
            if (!sword.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, sword);
                sword = ItemStack.EMPTY;
            }
        } finally {
            droppingContents = false;
        }
    }

    public void syncBlockState() {
        if (droppingContents || level == null || level.isClientSide) return;

        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof PedestalBlock)) return;

        int pendants = 0;
        if (!inventory.getStackInSlot(SLOT_POWER).isEmpty()) pendants |= 1;
        if (!inventory.getStackInSlot(SLOT_WISDOM).isEmpty()) pendants |= 2;
        if (!inventory.getStackInSlot(SLOT_COURAGE).isEmpty()) pendants |= 4;

        BlockState updated = state.setValue(PedestalBlock.PENDANTS, pendants)
                .setValue(PedestalBlock.UNLOCKED, pendants == 7)
                .setValue(PedestalBlock.HAS_SWORD, hasSword());
        if (!state.equals(updated)) level.setBlock(worldPosition, updated, Block.UPDATE_CLIENTS);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.zeldaswordskills_remastered.pedestal");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new PedestalMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("inventory", inventory.serializeNBT());
        tag.putBoolean(HAS_SWORD_TAG, hasSword());
        tag.putInt("pendants", getBlockState().getValue(PedestalBlock.PENDANTS));
        tag.putBoolean("unlocked", getBlockState().getValue(PedestalBlock.UNLOCKED));
        if (hasSword()) tag.put(SWORD_TAG, sword.save(new CompoundTag()));
        tag.putByte(ORIENTATION_TAG, orientation);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("inventory"));
        sword = tag.getBoolean(HAS_SWORD_TAG) && tag.contains(SWORD_TAG, Tag.TAG_COMPOUND)
                ? ItemStack.of(tag.getCompound(SWORD_TAG)) : ItemStack.EMPTY;
        orientation = (byte) (tag.getByte(ORIENTATION_TAG) & 1);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        syncBlockState();
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) load(tag);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandler.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandler = LazyOptional.of(() -> inventory);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) return itemHandler.cast();
        return super.getCapability(capability, side);
    }

    public static boolean isAllowedSword(ItemStack stack) {
        return !stack.isEmpty() && (stack.is(ZSSRegistries.MASTER_SWORD.get())
                || stack.is(ZSSRegistries.TEMPERED_SWORD.get())
                || stack.is(ZSSRegistries.GOLDEN_SWORD.get())
                || stack.is(ZSSRegistries.TRUE_MASTER_SWORD.get()));
    }

    private Item expectedPendant(int slot) {
        return switch (slot) {
            case SLOT_POWER -> ZSSRegistries.PENDANT_POWER.get();
            case SLOT_WISDOM -> ZSSRegistries.PENDANT_WISDOM.get();
            case SLOT_COURAGE -> ZSSRegistries.PENDANT_COURAGE.get();
            default -> throw new IllegalArgumentException("Unknown pedestal slot: " + slot);
        };
    }

    private static ItemStack copySwordForPedestal(ItemStack source) {
        if (!(source.getItem() instanceof ZeldaCombatItems.Sword)
                || !source.is(ZSSRegistries.GOLDEN_SWORD.get()) || !hasAllSacredFlames(source)) {
            ItemStack copy = source.copy();
            copy.setCount(1);
            return copy;
        }

        ItemStack trueMasterSword = new ItemStack(ZSSRegistries.TRUE_MASTER_SWORD.get());
        Map<Enchantment, Integer> enchantments = Map.of(
                Enchantments.SHARPNESS, 5,
                Enchantments.KNOCKBACK, 2,
                Enchantments.FIRE_ASPECT, 2,
                Enchantments.MOB_LOOTING, 3);
        EnchantmentHelper.setEnchantments(enchantments, trueMasterSword);
        return trueMasterSword;
    }

    private static boolean hasAllSacredFlames(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains("sacred_flames", Tag.TAG_LIST)) return false;
        ListTag flames = stack.getTag().getList("sacred_flames", Tag.TAG_STRING);
        Set<String> absorbed = new HashSet<>();
        for (int index = 0; index < flames.size(); index++) absorbed.add(flames.getString(index));
        return absorbed.contains("zeldaswordskills_remastered:din")
                && absorbed.contains("zeldaswordskills_remastered:farore")
                && absorbed.contains("zeldaswordskills_remastered:nayru");
    }
}
