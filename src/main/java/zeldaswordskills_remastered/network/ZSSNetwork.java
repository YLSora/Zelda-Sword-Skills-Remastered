package zeldaswordskills_remastered.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.PlayerCombatState;

import java.util.Optional;

public final class ZSSNetwork {
    /** Includes the Magic Mirror transition state. */
    private static final String PROTOCOL_VERSION = "19";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, "main"),
            () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
    private static boolean initialized;

    private ZSSNetwork() {
    }

    public static synchronized void initialize() {
        if (initialized) return;
        int id = 0;
        CHANNEL.registerMessage(id++, PlayerDataSyncMessage.class, PlayerDataSyncMessage::encode, PlayerDataSyncMessage::decode,
                PlayerDataSyncMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SkillIntentMessage.class, SkillIntentMessage::encode, SkillIntentMessage::decode,
                SkillIntentMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, CombatStateMessage.class, CombatStateMessage::encode, CombatStateMessage::decode,
                CombatStateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TargetIntentMessage.class, TargetIntentMessage::encode, TargetIntentMessage::decode,
                TargetIntentMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SongIntentMessage.class, SongIntentMessage::encode, SongIntentMessage::decode,
                SongIntentMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, SongStateMessage.class, SongStateMessage::encode, SongStateMessage::decode,
                SongStateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, BombBagCycleIntentMessage.class, BombBagCycleIntentMessage::encode, BombBagCycleIntentMessage::decode,
                BombBagCycleIntentMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, DodgeOrbitMessage.class, DodgeOrbitMessage::encode, DodgeOrbitMessage::decode,
                DodgeOrbitMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, ParryStateMessage.class, ParryStateMessage::encode, ParryStateMessage::decode,
                ParryStateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SpinStateMessage.class, SpinStateMessage::encode, SpinStateMessage::decode,
                SpinStateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, FlashAssaultStateMessage.class, FlashAssaultStateMessage::encode, FlashAssaultStateMessage::decode,
                FlashAssaultStateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, SkillToggleMessage.class, SkillToggleMessage::encode, SkillToggleMessage::decode,
                SkillToggleMessage::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id, MirrorStateMessage.class, MirrorStateMessage::encode, MirrorStateMessage::decode,
                MirrorStateMessage::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        initialized = true;
    }

    public static void syncPlayerData(ServerPlayer player) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        ZSSCapabilities.get(player).ifPresent(data -> CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player), new PlayerDataSyncMessage(data.save())));
    }

    public static void syncMirrorState(ServerPlayer player, boolean active) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new MirrorStateMessage(active));
    }

    public static void syncCombatState(ServerPlayer player, PlayerCombatState state) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CombatStateMessage(state.targetId(), state.comboCount(),
                state.comboMaximum(), state.comboDamage(), state.comboDeadline(), state.comboFinished(), state.focusUntil(), state.groundSlamReady(),
                state.iaiSlash().targetId(), state.dashImmuneUntil()));
    }

    public static void sendSkillIntent(SkillIntentMessage message) {
        CHANNEL.sendToServer(message);
    }

    public static void toggleSkill(ResourceLocation skill) { CHANNEL.sendToServer(new SkillToggleMessage(skill)); }

    public static void syncFlashAssault(ServerPlayer player, FlashAssaultStateMessage message) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void syncSpinState(ServerPlayer player, PlayerCombatState state) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SpinStateMessage(state.spinUntil()));
    }

    public static void sendParryState(ServerPlayer player, PlayerCombatState state) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        long now = player.level().getGameTime();
        int guard = (int) Math.max(0L, state.parryUntil() - now);
        int cooldown = (int) Math.max(0L, state.parryCooldownUntil() - now);
        int followUp = state.swordBreakActive(now) ? (int) (state.swordBreakUntil() - now) : 0;
        int helm = state.helmSplitter().ready(now) ? (int) (state.helmSplitter().readyUntil() - now) : 0;
        int attacker = helm > 0 ? state.helmSplitter().targetId() : followUp > 0 ? state.swordBreakTargetId() : -1;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ParryStateMessage(guard, cooldown, attacker, followUp, helm));
    }

    public static void startDodgeOrbit(ServerPlayer player, DodgeOrbitMessage message) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendTargetIntent(TargetIntentMessage.Action action) {
        CHANNEL.sendToServer(new TargetIntentMessage(action));
    }

    public static void sendSongIntent(SongIntentMessage message) {
        CHANNEL.sendToServer(message);
    }

    public static void sendBombBagCycleIntent() { CHANNEL.sendToServer(new BombBagCycleIntentMessage()); }

    public static void syncSongState(ServerPlayer player, SongStateMessage message) {
        if (player.connection == null || player.connection.connection.channel() == null || !player.connection.connection.isConnected()) return;
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
