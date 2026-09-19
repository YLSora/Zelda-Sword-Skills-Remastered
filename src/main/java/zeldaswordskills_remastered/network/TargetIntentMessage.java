package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.TargetingService;
import zeldaswordskills_remastered.config.ZSSConfig;

import java.util.function.Supplier;

public record TargetIntentMessage(Action action) {
    public static void encode(TargetIntentMessage message, FriendlyByteBuf buffer) {
        buffer.writeByte(message.action.ordinal());
    }

    public static TargetIntentMessage decode(FriendlyByteBuf buffer) {
        int action = buffer.readUnsignedByte();
        if (action >= Action.values().length) throw new DecoderException("Invalid target action");
        return new TargetIntentMessage(Action.values()[action]);
    }

    public static void handle(TargetIntentMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> ZSSCapabilities.get(sender).ifPresent(data -> {
                if (data.combat().tryAcceptTargetIntent(sender.level().getGameTime(), ZSSConfig.SERVER.intentsPerSecond.get())) {
                    TargetingService.handle(sender, data, message.action);
                }
            }));
        }
        context.setPacketHandled(true);
    }

    public enum Action {
        ACQUIRE,
        NEXT,
        CLEAR
    }
}
