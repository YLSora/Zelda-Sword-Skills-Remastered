package zeldaswordskills_remastered.entity.npc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import zeldaswordskills_remastered.quest.QuestService;
import zeldaswordskills_remastered.song.SongDefinition;
import zeldaswordskills_remastered.song.SongEntityListener;
import javax.annotation.Nullable;

public final class QuestNpc extends AbstractVillager implements SongEntityListener {
    private final Role role;
    private boolean waterBombUnlocked;
    private boolean fireBombUnlocked;

    public QuestNpc(EntityType<? extends QuestNpc> type, Level level, Role role) {
        super(type, level);
        this.role = role;
        setPersistenceRequired();
        if (role == Role.GORON) setCustomName(Component.literal("Biggoron"));
    }

    public Role role() {
        return role;
    }

    public boolean waterBombUnlocked() {
        return waterBombUnlocked;
    }

    public boolean fireBombUnlocked() {
        return fireBombUnlocked;
    }

    public boolean unlockWaterBomb() {
        if (waterBombUnlocked) return false;
        waterBombUnlocked = true;
        return true;
    }

    public boolean unlockFireBomb() {
        if (fireBombUnlocked) return false;
        fireBombUnlocked = true;
        return true;
    }

    public void openOffers(ServerPlayer player, MerchantOffers offers) {
        this.offers = offers;
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), 1);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55D));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            QuestService.interactNpc(serverPlayer, this, hand);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected void updateTrades() {
    }

    @Override
    protected void rewardTradeXp(MerchantOffer offer) {
    }

    @Nullable
    @Override
    public QuestNpc getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean showProgressBar() {
        return false;
    }

    @Override
    public boolean onSongPlayed(ServerPlayer player, SongDefinition song, int strength, int affectedBefore) {
        return QuestService.onSongPlayed(player, this, song, strength);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("water_bomb_unlocked", waterBombUnlocked);
        tag.putBoolean("fire_bomb_unlocked", fireBombUnlocked);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        waterBombUnlocked = tag.getBoolean("water_bomb_unlocked");
        fireBombUnlocked = tag.getBoolean("fire_bomb_unlocked");
    }

    @Override
    public Component getName() {
        return hasCustomName() ? getCustomName() : Component.translatable("entity.zeldaswordskills_remastered." + role.registryPath());
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public enum Role {
        ZELDA("npc/zelda"),
        MASK_TRADER("npc/mask_trader"),
        GORON("goron"),
        BARNES("npc/barnes"),
        ORCA("npc/orca");

        private final String registryPath;

        Role(String registryPath) {
            this.registryPath = registryPath;
        }

        public String registryPath() {
            return registryPath;
        }
    }
}
