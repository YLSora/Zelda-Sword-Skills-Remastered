package zeldaswordskills_remastered.song;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record SongDefinition(ResourceLocation id, int minimumDuration, List<SongNote> notes) {
    public SongDefinition {
        notes = List.copyOf(notes);
        if (minimumDuration <= 0 || notes.size() < 3 || notes.size() > 8) {
            throw new IllegalArgumentException("Invalid song definition: " + id);
        }
    }
}
