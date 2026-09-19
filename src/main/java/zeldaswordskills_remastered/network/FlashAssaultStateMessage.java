package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;
import java.util.Optional;

public record FlashAssaultStateMessage(int targetId, long readyUntil, long followUpUntil, int lockedSlot,
                                      boolean dashing, boolean attacking, long dashUntil, long cooldownUntil,
                                      Optional<Vec3> impulse) {
    public static void encode(FlashAssaultStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.targetId);
        buffer.writeLong(message.readyUntil);
        buffer.writeLong(message.followUpUntil);
        buffer.writeByte(message.lockedSlot);
        buffer.writeBoolean(message.dashing);
        buffer.writeBoolean(message.attacking);
        buffer.writeLong(message.dashUntil);
        buffer.writeLong(message.cooldownUntil);
        buffer.writeOptional(message.impulse, (buf, motion) -> {
            buf.writeDouble(motion.x);
            buf.writeDouble(motion.y);
            buf.writeDouble(motion.z);
        });
    }

    public static FlashAssaultStateMessage decode(FriendlyByteBuf buffer) {
        var message = new FlashAssaultStateMessage(buffer.readInt(), buffer.readLong(), buffer.readLong(),
                buffer.readByte(), buffer.readBoolean(), buffer.readBoolean(), buffer.readLong(), buffer.readLong(),
                buffer.readOptional(buf -> new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
        if (message.targetId < -1 || message.readyUntil < 0 || message.followUpUntil < 0
                || message.lockedSlot < -1 || message.lockedSlot > 8
                || message.dashUntil < 0 || message.cooldownUntil < 0 || (message.dashing && message.dashUntil == 0)
                || (message.impulse.isPresent() && !message.dashing)
                || message.impulse.filter(v -> !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)).isPresent()
                || ((message.dashing || message.attacking) != (message.lockedSlot >= 0))) {
            throw new DecoderException("Invalid Flash Assault state");
        }
        return message;
    }

    public static void handle(FlashAssaultStateMessage message, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> zeldaswordskills_remastered.client.ZSSClientFlashAssault.apply(message)));
        context.setPacketHandled(true);
    }
}
