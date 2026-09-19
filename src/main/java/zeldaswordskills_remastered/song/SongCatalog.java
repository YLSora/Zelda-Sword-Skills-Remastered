package zeldaswordskills_remastered.song;

import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.registry.ZSSContentIds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SongCatalog {
    private static final Map<ResourceLocation, SongDefinition> SONGS = new LinkedHashMap<>();

    static {
        register(ZSSContentIds.EPONA, 90, SongNote.D2, SongNote.B2, SongNote.A2, SongNote.D2, SongNote.B2, SongNote.A2);
        register(ZSSContentIds.HEALING, 78, SongNote.B2, SongNote.A2, SongNote.F1, SongNote.B2, SongNote.A2, SongNote.F1);
        register(ZSSContentIds.SARIA, 71, SongNote.F1, SongNote.A2, SongNote.B2, SongNote.F1, SongNote.A2, SongNote.B2);
        register(ZSSContentIds.SOARING, 120, SongNote.F1, SongNote.B2, SongNote.D2, SongNote.F1, SongNote.B2, SongNote.D2);
        register(ZSSContentIds.STORMS, 85, SongNote.D1, SongNote.F1, SongNote.D2, SongNote.D1, SongNote.F1, SongNote.D2);
        register(ZSSContentIds.SUN, 100, SongNote.A2, SongNote.F1, SongNote.D2, SongNote.A2, SongNote.F1, SongNote.D2);
        register(ZSSContentIds.TIME, 90, SongNote.A2, SongNote.D1, SongNote.F1, SongNote.A2, SongNote.D1, SongNote.F1);
        register(ZSSContentIds.BOLERO, 60, SongNote.F1, SongNote.D1, SongNote.F1, SongNote.D1, SongNote.A2, SongNote.F1, SongNote.A2, SongNote.F1);
        register(ZSSContentIds.MINUET, 90, SongNote.D1, SongNote.D2, SongNote.B2, SongNote.A2, SongNote.B2, SongNote.A2);
        register(ZSSContentIds.PRELUDE, 92, SongNote.D2, SongNote.A2, SongNote.D2, SongNote.A2, SongNote.B2, SongNote.D2);
        register(ZSSContentIds.OATH, 110, SongNote.A2, SongNote.F1, SongNote.D1, SongNote.F1, SongNote.A2, SongNote.D2);
        register(ZSSContentIds.NOCTURNE, 116, SongNote.B2, SongNote.A2, SongNote.A2, SongNote.D1, SongNote.B2, SongNote.A2, SongNote.F1);
        register(ZSSContentIds.REQUIEM, 125, SongNote.D1, SongNote.F1, SongNote.D1, SongNote.A2, SongNote.F1, SongNote.D1);
        register(ZSSContentIds.SERENADE, 83, SongNote.D1, SongNote.F1, SongNote.A2, SongNote.A2, SongNote.B2);
        register(ZSSContentIds.LULLABY, 129, SongNote.B2, SongNote.D2, SongNote.A2, SongNote.B2, SongNote.D2, SongNote.A2);
    }

    private SongCatalog() {
    }

    public static Map<ResourceLocation, SongDefinition> all() { return Map.copyOf(SONGS); }
    public static Optional<SongDefinition> get(ResourceLocation id) { return Optional.ofNullable(SONGS.get(id)); }

    public static Optional<SongDefinition> exact(List<SongNote> notes, java.util.Set<ResourceLocation> learned) {
        return SONGS.values().stream().filter(song -> learned.contains(song.id()) && song.notes().equals(notes)).findFirst();
    }

    public static boolean isLearnedPrefix(List<SongNote> notes, java.util.Set<ResourceLocation> learned) {
        return SONGS.values().stream().filter(song -> learned.contains(song.id()))
                .anyMatch(song -> startsWith(song.notes(), notes));
    }

    public static boolean isUniqueScarecrowMelody(List<SongNote> notes) {
        if (notes.size() != 8 || notes.stream().distinct().count() < 2) return false;
        return SONGS.values().stream().noneMatch(song -> startsWith(notes, song.notes()) || startsWith(song.notes(), notes));
    }

    private static boolean startsWith(List<SongNote> sequence, List<SongNote> prefix) {
        if (prefix.size() > sequence.size()) return false;
        for (int i = 0; i < prefix.size(); i++) if (sequence.get(i) != prefix.get(i)) return false;
        return true;
    }

    private static void register(ResourceLocation id, int duration, SongNote... notes) {
        SongDefinition definition = new SongDefinition(id, duration, List.of(notes));
        if (SONGS.putIfAbsent(id, definition) != null) throw new IllegalStateException("Duplicate song: " + id);
    }
}
