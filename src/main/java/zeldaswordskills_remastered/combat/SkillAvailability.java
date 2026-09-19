package zeldaswordskills_remastered.combat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.network.ZSSNetwork;
import zeldaswordskills_remastered.registry.ZSSContentIds;

/** Applies a player's preference and cancels the affected live effects on the server. */
public final class SkillAvailability {
    private SkillAvailability() {}

    public static boolean setEnabled(ServerPlayer player, ZSSPlayerData data, ResourceLocation skill, boolean enabled) {
        if (!data.setSkillEnabled(skill, enabled)) return false;
        if (!enabled) {
            var state = data.combat();
            long now = player.level().getGameTime();
            boolean basic = skill.equals(ZSSContentIds.SWORD_BASIC);
            boolean dodge = skill.equals(ZSSContentIds.DODGE);
            if (basic || dodge || skill.equals(ZSSContentIds.FLASH_ASSAULT)) FlashAssault.disable(player, state.flashAssault());
            if (((basic || skill.equals(ZSSContentIds.DASH)) && state.dashPending()) || (dodge && state.dodgeActive(now))) {
                player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
                player.hurtMarked = true;
            }
            if (skill.equals(ZSSContentIds.PARRY)) AdvancedSwordSkills.clearParryLocks(player);
            if (skill.equals(ZSSContentIds.SWORD_BEAM)) {
                for (var entity : player.serverLevel().getAllEntities()) {
                    if (entity instanceof zeldaswordskills_remastered.entity.projectile.SwordBeam beam && beam.getOwner() == player) beam.discard();
                }
            }
            state.disableSkill(skill, now);
            if (basic || skill.equals(ZSSContentIds.PARRY) || skill.equals(ZSSContentIds.SWORD_BREAK)
                    || skill.equals(ZSSContentIds.HELM_SPLITTER)) ZSSNetwork.sendParryState(player, state);
            if (skill.equals(ZSSContentIds.SPIN_ATTACK) || skill.equals(ZSSContentIds.SUPER_SPIN_ATTACK)) {
                ZSSNetwork.syncSpinState(player, state);
            }
            ZSSNetwork.syncCombatState(player, state);
        }
        ZSSNetwork.syncPlayerData(player);
        return true;
    }
}
