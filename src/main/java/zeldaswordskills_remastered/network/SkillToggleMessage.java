package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.combat.SkillAvailability;

/** The sender can only toggle their own learned skills; the server chooses the resulting state. */
public record SkillToggleMessage(ResourceLocation skill) {
    public static void encode(SkillToggleMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.skill.toString(), 128);
    }

    public static SkillToggleMessage decode(FriendlyByteBuf buffer) {
        ResourceLocation skill = ResourceLocation.tryParse(buffer.readUtf(128));
        if (skill == null) throw new DecoderException("Invalid skill resource ID");
        return new SkillToggleMessage(skill);
    }

    public static void handle(SkillToggleMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        var context = contextSupplier.get();
        var player = context.getSender();
        if (player != null) context.enqueueWork(() -> ZSSCapabilities.get(player).ifPresent(data -> {
            if (!player.isAlive() || !data.tryAcceptSkillIntent(player.level().getGameTime())) return;
            SkillAvailability.setEnabled(player, data, message.skill, !data.skillEnabled(message.skill));
        }));
        context.setPacketHandled(true);
    }
}
