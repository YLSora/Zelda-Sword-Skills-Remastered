package zeldaswordskills_remastered.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.progression.AcquisitionService;
import zeldaswordskills_remastered.entity.NaviCreature;
import zeldaswordskills_remastered.entity.NaviDialogueService;
import zeldaswordskills_remastered.item.SpecialItems;
import net.minecraft.world.item.Items;

@Mod.EventBusSubscriber(modid = ZeldaSwordSkills_Remastered.MOD_ID)
public final class ZSSQuestEvents {
    private ZSSQuestEvents() {
    }

    @SubscribeEvent
    public static void interactEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof NaviCreature) {
            if (event.getEntity().getItemInHand(event.getHand()).is(Items.GLASS_BOTTLE)) {
                InteractionResult result = event.getEntity() instanceof ServerPlayer player
                        ? SpecialItems.NaviBottle.capture(player, (NaviCreature) event.getTarget(), event.getHand())
                        : InteractionResult.SUCCESS;
                event.setCancellationResult(result);
                event.setCanceled(true);
                return;
            }
            if (event.getEntity() instanceof ServerPlayer player && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                    && player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()
                    && NaviDialogueService.interact(player, (NaviCreature) event.getTarget())) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            if (!event.getEntity().getItemInHand(event.getHand()).isEmpty()) event.setCanceled(true);
            return;
        }
        if (event.getEntity().level().isClientSide && event.getTarget() instanceof Villager villager
                && QuestService.isCursedMan(villager) && !event.getEntity().getItemInHand(event.getHand()).is(Items.NAME_TAG)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (AcquisitionService.interact(player, event.getTarget(), event.getHand())
                || event.getTarget() instanceof Villager villager && QuestService.interactVillager(player, villager, event.getHand())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void attackEntity(AttackEntityEvent event) {
        if (event.getTarget() instanceof NaviCreature) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer player && (QuestService.attackForTrade(player, event.getTarget())
                || AcquisitionService.repairGiantSword(player, event.getTarget()))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void combat(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) NaviDialogueService.markCombat(attacker);
        if (event.getEntity() instanceof ServerPlayer victim) NaviDialogueService.markCombat(victim);
    }
}
