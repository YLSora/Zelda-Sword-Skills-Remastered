package zeldaswordskills_remastered.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class InstrumentItem extends Item {
    private final int songStrength;

    public InstrumentItem(int songStrength, Properties properties) {
        super(properties.stacksTo(1));
        this.songStrength = Math.max(0, Math.min(5, songStrength));
    }

    public int songStrength() { return songStrength; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }
}
