package zeldaswordskills_remastered.client;

import net.minecraft.nbt.CompoundTag;
import zeldaswordskills_remastered.capability.ZSSPlayerData;

public final class ZSSClientData {
    private static final ZSSPlayerData PLAYER_DATA = new ZSSPlayerData();

    private ZSSClientData() {
    }

    public static void apply(CompoundTag tag) {
        var enabled = zeldaswordskills_remastered.registry.ZSSContentIds.SKILLS.stream()
                .filter(PLAYER_DATA::skillEnabled).toList();
        PLAYER_DATA.load(tag);
        enabled.stream().filter(id -> !PLAYER_DATA.skillEnabled(id)).forEach(ZSSClientGameplay::disableSkill);
    }

    public static ZSSPlayerData playerData() {
        return PLAYER_DATA;
    }
}
