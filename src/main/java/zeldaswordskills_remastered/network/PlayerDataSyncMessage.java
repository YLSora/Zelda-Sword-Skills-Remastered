package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.client.ZSSClientData;

import java.util.function.Supplier;

public record PlayerDataSyncMessage(CompoundTag data) {
    public static void encode(PlayerDataSyncMessage message, FriendlyByteBuf buffer) {
        buffer.writeNbt(message.data);
    }

    public static PlayerDataSyncMessage decode(FriendlyByteBuf buffer) {
        CompoundTag data = buffer.readNbt();
        if (data == null || data.getInt("version") != 1) {
            throw new DecoderException("Unsupported Zelda player data snapshot");
        }
        return new PlayerDataSyncMessage(data);
    }

    public static void handle(PlayerDataSyncMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ZSSClientData.apply(message.data));
        context.setPacketHandled(true);
    }
}
