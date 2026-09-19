package zeldaswordskills_remastered.registry;

import java.util.List;

/** OST titles retain the source OGG names; item IDs retain the texture names. */
public final class MusicDiscCatalog {
    public record Disc(String id, String title, int seconds) {}

    public static final List<Disc> DISCS = List.of(
            new Disc("music_disc_a_link_to_the_past", "A Link to the Past", 68),
            new Disc("music_disc_a_link_to_the_past_arrange", "A Link to the Past (Arranged)", 174),
            new Disc("music_disc_ballad_of_the_goddess", "Ballad of the Goddess", 96),
            new Disc("music_disc_breath_of_the_wild", "Breath of the Wild", 120),
            new Disc("music_disc_colgera_battle", "Colgera Battle", 324),
            new Disc("music_disc_dark_world", "Dark World", 144),
            new Disc("music_disc_dark_world_arrange", "Dark World (Arranged)", 172),
            new Disc("music_disc_demon_dragon_battle", "Demon Dragon Battle", 300),
            new Disc("music_disc_dragon_island", "Dragon Island", 97),
            new Disc("music_disc_gerudo_valley", "Gerudo Valley", 106),
            new Disc("music_disc_great_sea", "Great Sea", 161),
            new Disc("music_disc_guardian_battle", "Guardian Battle", 132),
            new Disc("music_disc_hylian_skies", "Hylian Skies", 214),
            new Disc("music_disc_hyrule_castle", "Hyrule Castle", 286),
            new Disc("music_disc_hyrule_field", "Hyrule Field", 206),
            new Disc("music_disc_lost_wood", "Lost Wood", 51),
            new Disc("music_disc_majoras_mask", "Majora's Mask", 156),
            new Disc("music_disc_midnas_lament", "Midna's Lament", 189),
            new Disc("music_disc_molduga_battle", "Molduga Battle", 163),
            new Disc("music_disc_ocarina_of_time", "Ocarina of Time", 91),
            new Disc("music_disc_song_of_time", "Song of Time", 35),
            new Disc("music_disc_song_of_storm", "Song of Storm", 135),
            new Disc("music_disc_spirit_tracks", "Spirit Tracks", 249),
            new Disc("music_disc_tears_of_the_kingdom", "Tears of the Kingdom", 79),
            new Disc("music_disc_the_legend_of_zelda", "The Legend of Zelda", 108),
            new Disc("music_disc_twilight_princess", "Twilight Princess", 69),
            new Disc("music_disc_zelda_princess", "Zelda Princess", 59));

    private MusicDiscCatalog() {}
}
