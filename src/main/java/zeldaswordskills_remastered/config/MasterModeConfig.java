package zeldaswordskills_remastered.config;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;

public final class MasterModeConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> REGENERATION_BLACKLIST;

    static {
        var builder = new ForgeConfigSpec.Builder();
        REGENERATION_BLACKLIST = builder.comment("Entity IDs excluded from Master Mode health regeneration, e.g. minecraft:wither. Does not affect doubled health or damage.")
                .defineListAllowEmpty("regenerationBlacklist", List.of(),
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        SPEC = builder.build();
    }

    private MasterModeConfig() {}
}
