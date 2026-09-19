package zeldaswordskills_remastered.song;

public enum SongNote {
    D1(0.70710677F, 10, 0),
    F1(0.8408964F, 8, 1),
    A2(1.0594631F, 6, 2),
    B2(1.1892071F, 5, 3),
    D2(1.4142135F, 3, 4);

    private final float pitch;
    private final int staffRow;
    private final int spriteRow;

    SongNote(float pitch, int staffRow, int spriteRow) {
        this.pitch = pitch;
        this.staffRow = staffRow;
        this.spriteRow = spriteRow;
    }

    public float pitch() { return pitch; }
    public int staffRow() { return staffRow; }
    public int spriteRow() { return spriteRow; }
}
