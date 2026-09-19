package zeldaswordskills_remastered.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import zeldaswordskills_remastered.block.entity.PedestalBlockEntity;
import zeldaswordskills_remastered.block.PedestalBlock;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class PedestalMenu extends AbstractContainerMenu {
    private static final int PEDESTAL_SLOTS = PedestalBlockEntity.SLOT_COUNT;
    private static final int PLAYER_INVENTORY_START = PEDESTAL_SLOTS;
    private static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 27;
    private static final int HOTBAR_END = PLAYER_INVENTORY_END + 9;

    private final PedestalBlockEntity pedestal;

    public PedestalMenu(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        this(containerId, playerInventory, getPedestal(playerInventory, data));
    }

    public PedestalMenu(int containerId, Inventory playerInventory, PedestalBlockEntity pedestal) {
        super(ZSSRegistries.PEDESTAL_MENU.get(), containerId);
        this.pedestal = pedestal;
        ItemStackHandler inventory = pedestal.inventory();

        addSlot(new PedestalSlot(inventory, PedestalBlockEntity.SLOT_POWER, 80, 18, pedestal));
        addSlot(new PedestalSlot(inventory, PedestalBlockEntity.SLOT_WISDOM, 49, 49, pedestal));
        addSlot(new PedestalSlot(inventory, PedestalBlockEntity.SLOT_COURAGE, 111, 49, pedestal));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return !pedestal.isRemoved() && player.distanceToSqr(
                pedestal.getBlockPos().getX() + 0.5D,
                pedestal.getBlockPos().getY() + 0.5D,
                pedestal.getBlockPos().getZ() + 0.5D) <= 64.0D;
    }

    public int pendants() {
        if (!pedestal.getBlockState().hasProperty(PedestalBlock.PENDANTS)) return 0;
        return pedestal.getBlockState().getValue(PedestalBlock.UNLOCKED)
                ? 7 : pedestal.getBlockState().getValue(PedestalBlock.PENDANTS);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index < PEDESTAL_SLOTS) {
            if (!moveItemStackTo(source, PLAYER_INVENTORY_START, HOTBAR_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(source, 0, PEDESTAL_SLOTS, false)) {
            if (index < PLAYER_INVENTORY_END) {
                if (!moveItemStackTo(source, PLAYER_INVENTORY_END, HOTBAR_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(source, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, source);
        return copy;
    }

    private static PedestalBlockEntity getPedestal(Inventory playerInventory, FriendlyByteBuf data) {
        BlockEntity blockEntity = playerInventory.player.level().getBlockEntity(data.readBlockPos());
        if (blockEntity instanceof PedestalBlockEntity pedestal) {
            return pedestal;
        }
        throw new IllegalStateException("Pedestal menu opened without a pedestal block entity");
    }

    private static final class PedestalSlot extends SlotItemHandler {
        private final PedestalBlockEntity pedestal;

        private PedestalSlot(ItemStackHandler inventory, int slot, int x, int y, PedestalBlockEntity pedestal) {
            super(inventory, slot, x, y);
            this.pedestal = pedestal;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !pedestal.isUnlocked() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return super.mayPickup(player);
        }
    }
}
