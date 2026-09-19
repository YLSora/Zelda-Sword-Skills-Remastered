package zeldaswordskills_remastered.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.progression.ZSSAdvancementService;

/** Unlocks the blacksmith trade that tempers the Master Sword. */
public final class MasterOreItem extends Item {
    public MasterOreItem(Properties properties) { super(properties); }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, net.minecraft.world.entity.player.Player player,
                                                             LivingEntity target, InteractionHand hand) {
        if (!(player instanceof ServerPlayer) || !(target instanceof Villager villager) || villager.isBaby()) return InteractionResult.PASS;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        if (profession != VillagerProfession.WEAPONSMITH && profession != VillagerProfession.TOOLSMITH
                && profession != VillagerProfession.ARMORER) return InteractionResult.PASS;
        boolean exists = villager.getOffers().stream().anyMatch(offer -> offer.getResult().is(ZSSRegistries.TEMPERED_SWORD.get()));
        if (!exists) {
            villager.getOffers().add(new MerchantOffer(new ItemStack(this, 2),
                    new ItemStack(ZSSRegistries.MASTER_SWORD.get()), new ItemStack(ZSSRegistries.TEMPERED_SWORD.get()),
                    1, 30, 0.0F));
            ZSSAdvancementService.swordProgress((ServerPlayer) player, "sword.tempered");
        }
        return InteractionResult.CONSUME;
    }
}
