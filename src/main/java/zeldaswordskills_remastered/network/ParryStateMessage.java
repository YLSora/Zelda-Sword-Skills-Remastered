package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server snapshot of the guard, its next available time and the optional one-shot follow-up. */
public record ParryStateMessage(int guardTicks, int cooldownTicks, int attackerId, int followUpTicks, int helmTicks) {
    /** Longest follow-up the server can grant; anything past it is a malformed packet. */
    public static final int MAXIMUM_FOLLOW_UP_TICKS = 30;

    public static void encode(ParryStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.guardTicks);
        buffer.writeVarInt(message.cooldownTicks);
        buffer.writeVarInt(message.attackerId);
        buffer.writeVarInt(message.followUpTicks);
        buffer.writeVarInt(message.helmTicks);
    }

    public static ParryStateMessage decode(FriendlyByteBuf buffer) {
        int guardTicks = buffer.readVarInt();
        int cooldownTicks = buffer.readVarInt();
        int attackerId = buffer.readVarInt();
        int ticks = buffer.readVarInt();
        int helmTicks = buffer.readVarInt();
        if (guardTicks < 0 || guardTicks > 30 || cooldownTicks < 0 || cooldownTicks > 90
                || guardTicks > 0 && (cooldownTicks != guardTicks + 60 || ticks != 0 || helmTicks != 0)) {
            throw new DecoderException("Invalid parry guard state");
        }
        if (ticks < 0 || ticks > MAXIMUM_FOLLOW_UP_TICKS) throw new DecoderException("Invalid parry follow-up window");
        if (helmTicks < 0 || helmTicks > MAXIMUM_FOLLOW_UP_TICKS) throw new DecoderException("Invalid Helm Splitter window");
        if ((ticks > 0 || helmTicks > 0) && attackerId < 0) throw new DecoderException("A parry follow-up needs an attacker");
        return new ParryStateMessage(guardTicks, cooldownTicks, attackerId, ticks, helmTicks);
    }

    public static void handle(ParryStateMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> zeldaswordskills_remastered.client.ZSSClientGameplay.applyParryState(message)));
        context.setPacketHandled(true);
    }
}
