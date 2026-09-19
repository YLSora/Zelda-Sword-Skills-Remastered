package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.client.ZSSClientCombatState;

import java.util.function.Supplier;

public record CombatStateMessage(int targetId, int comboCount, int comboMaximum, float comboDamage,
                                 long comboDeadline, boolean comboFinished, long focusUntil, boolean groundSlamReady, int iaiTargetId,
                                 long dashImmuneUntil) {
    public static void encode(CombatStateMessage message, FriendlyByteBuf buffer) {
        int maximum = Math.max(0, Math.min(20, message.comboMaximum));
        int count = Math.max(0, Math.min(maximum, message.comboCount));
        float damage = Float.isFinite(message.comboDamage) ? Math.max(0.0F, Math.min(100000.0F, message.comboDamage)) : 0.0F;
        buffer.writeVarInt(Math.max(-1, message.targetId) + 1);
        buffer.writeByte(count);
        buffer.writeByte(maximum);
        buffer.writeFloat(damage);
        buffer.writeVarLong(Math.max(0L, message.comboDeadline));
        buffer.writeBoolean(message.comboFinished);
        buffer.writeVarLong(Math.max(0L, message.focusUntil));
        buffer.writeBoolean(message.groundSlamReady);
        buffer.writeVarInt(Math.max(-1, message.iaiTargetId) + 1);
        buffer.writeVarLong(Math.max(0L, message.dashImmuneUntil));
    }

    public static CombatStateMessage decode(FriendlyByteBuf buffer) {
        int targetId = buffer.readVarInt() - 1;
        int comboCount = buffer.readUnsignedByte();
        int comboMaximum = buffer.readUnsignedByte();
        float comboDamage = buffer.readFloat();
        long comboDeadline = buffer.readVarLong();
        boolean comboFinished = buffer.readBoolean();
        long focusUntil = buffer.readVarLong();
        boolean groundSlamReady = buffer.readBoolean();
        int iaiTargetId = buffer.readVarInt() - 1;
        long dashImmuneUntil = buffer.readVarLong();
        if (targetId < -1 || comboCount > 20 || comboMaximum > 20 || comboCount > comboMaximum
                || !Float.isFinite(comboDamage) || comboDamage < 0.0F || comboDamage > 100000.0F || comboDeadline < 0L || focusUntil < 0L
                || iaiTargetId < -1 || targetId >= 0 && iaiTargetId >= 0 || dashImmuneUntil < 0L) {
            throw new DecoderException("Invalid combat state");
        }
        return new CombatStateMessage(targetId, comboCount, comboMaximum, comboDamage, comboDeadline, comboFinished, focusUntil, groundSlamReady, iaiTargetId, dashImmuneUntil);
    }

    public static void handle(CombatStateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ZSSClientCombatState.apply(message.targetId, message.comboCount, message.comboMaximum,
                message.comboDamage, message.comboDeadline, message.comboFinished, message.focusUntil, message.groundSlamReady, message.iaiTargetId, message.dashImmuneUntil));
        context.setPacketHandled(true);
    }
}
