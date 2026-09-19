package zeldaswordskills_remastered.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.ItemStackHandler;
import zeldaswordskills_remastered.block.entity.PedestalBlockEntity;
import zeldaswordskills_remastered.registry.ZSSRegistries;

public final class PedestalItem extends BlockItem {
    public PedestalItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        InteractionResult result = super.place(context);
        if (!context.getLevel().isClientSide && result.consumesAction()
                && context.getLevel().getBlockEntity(context.getClickedPos()) instanceof PedestalBlockEntity pedestal) {
            pedestal.syncBlockState();
        }
        return result;
    }

    public static ItemStack withInsertedSword(Item item, ItemStack sword) {
        ItemStack stack = new ItemStack(item);
        ItemStackHandler inventory = new ItemStackHandler(PedestalBlockEntity.SLOT_COUNT);

        CompoundTag blockEntityTag = new CompoundTag();
        blockEntityTag.put("inventory", inventory.serializeNBT());
        blockEntityTag.putBoolean(PedestalBlockEntity.HAS_SWORD_TAG, true);
        blockEntityTag.putInt("pendants", 0);
        blockEntityTag.putBoolean("unlocked", false);
        blockEntityTag.put(PedestalBlockEntity.SWORD_TAG, sword.copy().save(new CompoundTag()));
        blockEntityTag.putByte(PedestalBlockEntity.ORIENTATION_TAG, (byte) 0);
        BlockItem.setBlockEntityData(stack, ZSSRegistries.PEDESTAL_BLOCK_ENTITY.get(), blockEntityTag);
        return stack;
    }
}
