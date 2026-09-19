package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Only the server can confirm that another paid spin has actually started. */
public record SpinStateMessage(long until) {
    public static void encode(SpinStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarLong(message.until);
    }

    public static SpinStateMessage decode(FriendlyByteBuf buffer) {
        long until = buffer.readVarLong();
        if (until < 0L) throw new DecoderException("Invalid spin deadline");
        return new SpinStateMessage(until);
    }

    public static void handle(SpinStateMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> zeldaswordskills_remastered.client.ZSSClientGameplay.applySpinState(message.until)));
        context.setPacketHandled(true);
    }
}
