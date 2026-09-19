package zeldaswordskills_remastered.client;

import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.network.SongStateMessage;
import zeldaswordskills_remastered.song.SongNote;

import java.util.List;
import java.util.Optional;

public final class ZSSClientSongState {
    private static SongStateMessage.Status status = SongStateMessage.Status.CLOSED;
    private static List<SongNote> notes = List.of();
    private static Optional<ResourceLocation> song = Optional.empty();
    private static long deadline;
    private static boolean scarecrowMode;
    private static long revision;

    private ZSSClientSongState() {
    }

    public static void beginLocal(boolean scarecrow) {
        status = SongStateMessage.Status.OPEN;
        notes = List.of();
        song = Optional.empty();
        deadline = 0L;
        scarecrowMode = scarecrow;
        revision++;
    }

    public static void apply(SongStateMessage message) {
        status = message.status();
        notes = message.notes();
        song = message.song();
        deadline = message.deadline();
        scarecrowMode = message.scarecrowMode();
        revision++;
    }

    public static void clear() {
        status = SongStateMessage.Status.CLOSED;
        notes = List.of();
        song = Optional.empty();
        deadline = 0L;
        scarecrowMode = false;
        revision++;
    }

    public static SongStateMessage.Status status() { return status; }
    public static List<SongNote> notes() { return notes; }
    public static Optional<ResourceLocation> song() { return song; }
    public static long deadline() { return deadline; }
    public static boolean scarecrowMode() { return scarecrowMode; }
    public static long revision() { return revision; }
}
