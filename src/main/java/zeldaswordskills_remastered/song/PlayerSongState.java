package zeldaswordskills_remastered.song;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSongState {
    private boolean open;
    private boolean scarecrowMode;
    private ResourceLocation instrumentId;
    private final List<SongNote> notes = new ArrayList<>();
    private final List<SongNote> hudNotes = new ArrayList<>();
    private ResourceLocation matchedSong;
    private long completionDeadline;
    private long recognitionDeadline;
    private long lastNoteTick = Long.MIN_VALUE;

    public void open(ResourceLocation instrument, boolean scarecrow) {
        reset();
        open = true;
        instrumentId = instrument;
        scarecrowMode = scarecrow;
    }

    public void reset() {
        open = false;
        scarecrowMode = false;
        instrumentId = null;
        notes.clear();
        hudNotes.clear();
        matchedSong = null;
        completionDeadline = 0L;
        recognitionDeadline = 0L;
        lastNoteTick = Long.MIN_VALUE;
    }

    public boolean addNote(SongNote note, long now) {
        if (!open || matchedSong != null || lastNoteTick == now) return false;
        if (hudNotes.size() == 8) hudNotes.clear();
        if (notes.size() == 8) notes.clear();
        notes.add(note);
        hudNotes.add(note);
        lastNoteTick = now;
        return true;
    }

    public void clearCandidate() { notes.clear(); matchedSong = null; completionDeadline = 0L; }
    public void restartCandidate(SongNote note, boolean keepNote) {
        clearCandidate();
        if (keepNote) notes.add(note);
    }
    public void waitForRecognition(long deadline) { recognitionDeadline = deadline; }
    public void clearRecognitionDeadline() { recognitionDeadline = 0L; }
    public void match(ResourceLocation song, long deadline) {
        matchedSong = song;
        completionDeadline = deadline;
        recognitionDeadline = 0L;
    }
    public boolean open() { return open; }
    public boolean scarecrowMode() { return scarecrowMode; }
    public ResourceLocation instrumentId() { return instrumentId; }
    public List<SongNote> notes() { return List.copyOf(notes); }
    public List<SongNote> hudNotes() { return List.copyOf(hudNotes); }
    public ResourceLocation matchedSong() { return matchedSong; }
    public long completionDeadline() { return completionDeadline; }
    public long recognitionDeadline() { return recognitionDeadline; }
}
