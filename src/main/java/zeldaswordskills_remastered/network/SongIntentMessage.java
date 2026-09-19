package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.song.SongNote;
import zeldaswordskills_remastered.song.SongService;

import java.util.Optional;
import java.util.function.Supplier;

public record SongIntentMessage(Action action, Optional<SongNote> note, int semitoneOffset) {
    public SongIntentMessage(Action action) { this(action, Optional.empty(), 0); }

    public static void encode(SongIntentMessage message, FriendlyByteBuf buffer) {
        buffer.writeByte(message.action.ordinal());
        if (message.action == Action.NOTE) {
            buffer.writeByte(message.note.orElseThrow().ordinal());
            buffer.writeByte(message.semitoneOffset);
        }
    }

    public static SongIntentMessage decode(FriendlyByteBuf buffer) {
        int action = buffer.readUnsignedByte();
        if (action >= Action.values().length) throw new DecoderException("Invalid song action");
        Action decoded = Action.values()[action];
        if (decoded != Action.NOTE) return new SongIntentMessage(decoded);
        int note = buffer.readUnsignedByte();
        if (note >= SongNote.values().length) throw new DecoderException("Invalid song note");
        int semitoneOffset = buffer.readByte();
        if (semitoneOffset < -1 || semitoneOffset > 1) throw new DecoderException("Invalid song semitone offset");
        return new SongIntentMessage(decoded, Optional.of(SongNote.values()[note]), semitoneOffset);
    }

    public static void handle(SongIntentMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) context.enqueueWork(() -> ZSSCapabilities.get(sender).ifPresent(data -> {
            if (!data.tryAcceptSkillIntent(sender.level().getGameTime())) return;
            switch (message.action) {
                case BEGIN -> SongService.begin(sender, data, false);
                case BEGIN_SCARECROW -> SongService.begin(sender, data, true);
                case NOTE -> message.note.ifPresent(note -> SongService.note(sender, data, note, message.semitoneOffset));
                case FREE_PLAY_ON -> SongService.setFreePlay(sender, data, true);
                case FREE_PLAY_OFF -> SongService.setFreePlay(sender, data, false);
                case CANCEL -> SongService.cancel(sender, data);
            }
        }));
        context.setPacketHandled(true);
    }

    public enum Action { BEGIN, BEGIN_SCARECROW, NOTE, CANCEL, FREE_PLAY_ON, FREE_PLAY_OFF }
}
