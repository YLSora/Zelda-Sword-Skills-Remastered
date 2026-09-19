package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.combat.BasicSwordSkill;
import zeldaswordskills_remastered.combat.AdvancedSwordSkills;

import java.util.Optional;
import java.util.function.Supplier;

public record SkillIntentMessage(ResourceLocation skill, Action action, Optional<Vec3> direction) {
    public static void encode(SkillIntentMessage message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.skill.toString(), 128);
        buffer.writeByte(message.action.ordinal());
        buffer.writeBoolean(message.direction.isPresent());
        message.direction.ifPresent(direction -> {
            buffer.writeFloat((float) direction.x);
            buffer.writeFloat((float) direction.y);
            buffer.writeFloat((float) direction.z);
        });
    }

    public static SkillIntentMessage decode(FriendlyByteBuf buffer) {
        ResourceLocation skill = ResourceLocation.tryParse(buffer.readUtf(128));
        if (skill == null) throw new DecoderException("Invalid skill resource ID");
        int actionIndex = buffer.readUnsignedByte();
        if (actionIndex >= Action.values().length) throw new DecoderException("Invalid skill action");
        Optional<Vec3> direction = Optional.empty();
        if (buffer.readBoolean()) {
            Vec3 value = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
            if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z) || value.lengthSqr() > 1.01D) {
                throw new DecoderException("Invalid skill direction");
            }
            direction = Optional.of(value);
        }
        return new SkillIntentMessage(skill, Action.values()[actionIndex], direction);
    }

    public static void handle(SkillIntentMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> ZSSCapabilities.get(sender).ifPresent(data -> {
                if (!data.tryAcceptSkillIntent(sender.level().getGameTime())) {
                    ZeldaSwordSkills_Remastered.LOGGER.debug("Rejected rate-limited skill intent from {}", sender.getGameProfile().getName());
                    return;
                }
                if (!ZSSContentIds.SKILLS.contains(message.skill) || data.activeSkillLevel(message.skill) <= 0) {
                    ZeldaSwordSkills_Remastered.LOGGER.debug("Rejected unknown or unlearned skill intent {} from {}", message.skill, sender.getGameProfile().getName());
                    return;
                }
                if (message.skill.equals(ZSSContentIds.SWORD_BASIC)) {
                    BasicSwordSkill.handleIntent(sender, data, message);
                } else AdvancedSwordSkills.handleIntent(sender, data, message);
            }));
        }
        context.setPacketHandled(true);
    }

    public enum Action {
        BEGIN,
        RELEASE,
        CANCEL,
        ATTACK
    }
}
