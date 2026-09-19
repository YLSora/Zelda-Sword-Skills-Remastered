package zeldaswordskills_remastered.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record MirrorStateMessage(boolean active) {
    public static void encode(MirrorStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.active);
    }

    public static MirrorStateMessage decode(FriendlyByteBuf buffer) {
        return new MirrorStateMessage(buffer.readBoolean());
    }

    public static void handle(MirrorStateMessage message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> zeldaswordskills_remastered.client.MagicMirrorEffect.apply(message.active)));
        context.setPacketHandled(true);
    }
}
