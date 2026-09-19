package zeldaswordskills_remastered.quest;

import net.minecraft.resources.ResourceLocation;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;

public final class QuestStates {
    public static final ResourceLocation NOT_STARTED = id("not_started");
    public static final ResourceLocation STARTED = id("started");
    public static final ResourceLocation OCARINA_HELD = id("ocarina_held");
    public static final ResourceLocation BORROWED = id("borrowed");
    public static final ResourceLocation SOLD = id("sold");
    public static final ResourceLocation PAID = id("paid");
    public static final ResourceLocation CLAIM_WAIT = id("claim_wait");
    public static final ResourceLocation COMPLETE = id("complete");

    private QuestStates() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }
}
