package zeldaswordskills_remastered.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import zeldaswordskills_remastered.client.ZSSClientSongState;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.song.SongNote;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public record SongStateMessage(Status status, List<SongNote> notes, Optional<ResourceLocation> song,
                               long deadline, boolean scarecrowMode) {
    public SongStateMessage {
        notes = List.copyOf(notes);
    }

    public static void encode(SongStateMessage message, FriendlyByteBuf buffer) {
        buffer.writeByte(message.status.ordinal());
        buffer.writeByte(message.notes.size());
        message.notes.forEach(note -> buffer.writeByte(note.ordinal()));
        buffer.writeBoolean(message.song.isPresent());
        message.song.ifPresent(buffer::writeResourceLocation);
        buffer.writeVarLong(message.deadline);
        buffer.writeBoolean(message.scarecrowMode);
    }

    public static SongStateMessage decode(FriendlyByteBuf buffer) {
        int status = buffer.readUnsignedByte();
        int size = buffer.readUnsignedByte();
        if (status >= Status.values().length || size > 8) throw new DecoderException("Invalid song HUD state");
        List<SongNote> notes = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            int note = buffer.readUnsignedByte();
            if (note >= SongNote.values().length) throw new DecoderException("Invalid song HUD note");
            notes.add(SongNote.values()[note]);
        }
        Optional<ResourceLocation> song = buffer.readBoolean() ? Optional.of(buffer.readResourceLocation()) : Optional.empty();
        if (song.isPresent() && !ZSSContentIds.SONGS.contains(song.get())) throw new DecoderException("Unknown song HUD ID");
        long deadline = buffer.readVarLong();
        if (deadline < 0L) throw new DecoderException("Invalid song deadline");
        return new SongStateMessage(Status.values()[status], notes, song, deadline, buffer.readBoolean());
    }

    public static void handle(SongStateMessage message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ZSSClientSongState.apply(message));
        context.setPacketHandled(true);
    }

    public enum Status { OPEN, MATCHED, FAILED, RECORDED, SUCCESS, CLOSED }
}
