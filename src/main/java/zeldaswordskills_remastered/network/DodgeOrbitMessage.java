package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server-approved orbit launch. No client-reported positions or skill results. */
public record DodgeOrbitMessage(int targetId, boolean right, double speed) {
    public static void encode(DodgeOrbitMessage message, FriendlyByteBuf buffer) {
        buffer.writeVarInt(message.targetId);
        buffer.writeBoolean(message.right);
        buffer.writeDouble(message.speed);
    }

    public static DodgeOrbitMessage decode(FriendlyByteBuf buffer) {
        int target = buffer.readVarInt();
        boolean right = buffer.readBoolean();
        double speed = buffer.readDouble();
        if (target < 0 || !Double.isFinite(speed) || speed <= 0.0D
                || speed > zeldaswordskills_remastered.combat.DodgeMovement.DISTANCE)
            throw new DecoderException("Invalid Dodge orbit launch");
        return new DodgeOrbitMessage(target, right, speed);
    }

    public static void handle(DodgeOrbitMessage message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> zeldaswordskills_remastered.client.ZSSClientDodge.start(message)));
        context.setPacketHandled(true);
    }
}
