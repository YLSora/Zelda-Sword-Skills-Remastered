package zeldaswordskills_remastered.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.item.BombBagItem;

import java.util.function.Supplier;

public record BombBagCycleIntentMessage() {
    public static void encode(BombBagCycleIntentMessage message, FriendlyByteBuf buffer) { }
    public static BombBagCycleIntentMessage decode(FriendlyByteBuf buffer) { return new BombBagCycleIntentMessage(); }
    public static void handle(BombBagCycleIntentMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> ZSSCapabilities.get(sender).ifPresent(data -> {
            if (data.tryAcceptSkillIntent(sender.level().getGameTime())) BombBagItem.cycleSelected(sender);
        }));
        context.setPacketHandled(true);
    }
}
