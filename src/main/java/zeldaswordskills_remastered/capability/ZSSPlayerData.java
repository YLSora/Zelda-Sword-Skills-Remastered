package zeldaswordskills_remastered.capability;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.config.ZSSConfig;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.combat.PlayerCombatState;
import zeldaswordskills_remastered.song.PlayerSongState;
import zeldaswordskills_remastered.song.SongNote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class ZSSPlayerData {
    public static final int MAX_SKILLS = 64;
    public static final int MAX_SONGS = 32;
    public static final int MAX_WARPS = 32;
    public static final int MAX_QUESTS = 32;
    public static final int MAX_HEALED_NPCS = 256;
    public static final int MAX_SCARECROW_NOTES = 8;
    private float currentMagic;
    private float maxMagic;
    private int secretRooms;
    private final Set<ResourceLocation> completedDungeons = new java.util.HashSet<>();
    private boolean receivedStartingGear;
    private int skulltulaTokens;
    private int skulltulaTrades;
    private int knightsCrestsGiven;
    private int orcaChoiceTier;
    private final Map<ResourceLocation, Integer> skills = new LinkedHashMap<>();
    private final Set<ResourceLocation> disabledSkills = new LinkedHashSet<>();
    private final Set<ResourceLocation> songs = new LinkedHashSet<>();
    private final List<String> scarecrowNotes = new ArrayList<>();
    private long scarecrowConfirmedAt;
    private final Map<ResourceLocation, WarpPoint> warpPoints = new LinkedHashMap<>();
    private long healingCooldownUntil;
    private UUID horseUuid;
    private long horseChunk;
    private final Set<UUID> healedNpcs = new LinkedHashSet<>();
    private final Map<ResourceLocation, QuestProgress> quests = new LinkedHashMap<>();
    private ResourceLocation borrowedMask;
    private ItemStack escrowOcarina = ItemStack.EMPTY;
    private final PlayerCombatState combat = new PlayerCombatState();
    private final PlayerSongState songState = new PlayerSongState();
    private long skillIntentWindowStart = Long.MIN_VALUE;
    private int skillIntentsInWindow;

    public ZSSPlayerData() {
        maxMagic = Math.min((float) ZSSConfig.SERVER.startingMagic.get().doubleValue(), configuredMaximumMagic());
        currentMagic = maxMagic;
    }

    public CompoundTag save() {
        clampValues();
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        CompoundTag magic = new CompoundTag();
        magic.putFloat("current_magic", currentMagic);
        magic.putFloat("max_magic", maxMagic);
        magic.putInt("secret_rooms", secretRooms);
        magic.put("completed_dungeons", saveIds(completedDungeons));
        magic.putBoolean("received_starting_gear", receivedStartingGear);
        magic.putInt("skulltula_tokens", skulltulaTokens);
        magic.putInt("skulltula_trades", skulltulaTrades);
        magic.putInt("knights_crests_given", knightsCrestsGiven);
        magic.putInt("orca_choice_tier", orcaChoiceTier);
        root.put("magic_stats", magic);

        root.put("skills", saveLevels(skills));
        root.put("disabled_skills", saveIds(disabledSkills));
        CompoundTag songData = new CompoundTag();
        songData.put("learned", saveIds(songs));
        ListTag notes = new ListTag();
        scarecrowNotes.forEach(note -> notes.add(StringTag.valueOf(note)));
        songData.put("scarecrow_notes", notes);
        songData.putLong("scarecrow_confirmed_at", scarecrowConfirmedAt);
        songData.putLong("healing_cooldown_until", healingCooldownUntil);
        ListTag warps = new ListTag();
        warpPoints.forEach((id, point) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putString("dimension", point.dimension().toString());
            entry.putLong("pos", point.pos().asLong());
            warps.add(entry);
        });
        songData.put("warp_points", warps);
        if (horseUuid != null) {
            songData.putUUID("horse_uuid", horseUuid);
            songData.putLong("horse_chunk", horseChunk);
        }
        ListTag healed = new ListTag();
        healedNpcs.forEach(uuid -> healed.add(StringTag.valueOf(uuid.toString())));
        songData.put("healed_npcs", healed);
        root.put("songs", songData);

        ListTag questList = new ListTag();
        quests.forEach((id, progress) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putString("state", progress.state().toString());
            entry.putInt("step", progress.step());
            entry.putLong("updated_at", progress.updatedAt());
            CompoundTag counters = new CompoundTag();
            progress.counters().forEach(counters::putInt);
            entry.put("counters", counters);
            questList.add(entry);
        });
        root.put("quests", questList);
        if (borrowedMask != null) {
            root.putString("borrowed_mask", borrowedMask.toString());
        }
        if (!escrowOcarina.isEmpty()) {
            root.put("quest_escrow_ocarina", escrowOcarina.save(new CompoundTag()));
        }
        return root;
    }

    public void load(CompoundTag root) {
        combat.reset();
        songState.reset();
        skillIntentWindowStart = Long.MIN_VALUE;
        skillIntentsInWindow = 0;
        skills.clear();
        disabledSkills.clear();
        songs.clear();
        scarecrowNotes.clear();
        warpPoints.clear();
        healedNpcs.clear();
        quests.clear();
        borrowedMask = null;
        escrowOcarina = ItemStack.EMPTY;
        CompoundTag magic = root.getCompound("magic_stats");
        currentMagic = magic.getFloat("current_magic");
        maxMagic = magic.contains("max_magic", Tag.TAG_ANY_NUMERIC) ? magic.getFloat("max_magic")
                : Math.min((float) ZSSConfig.SERVER.startingMagic.get().doubleValue(), configuredMaximumMagic());
        secretRooms = magic.getInt("secret_rooms");
        completedDungeons.clear();
        loadIds(magic.getList("completed_dungeons", Tag.TAG_STRING), completedDungeons, 7,
                java.util.Arrays.stream(zeldaswordskills_remastered.worldgen.DungeonType.values())
                        .map(zeldaswordskills_remastered.worldgen.DungeonType::id).collect(java.util.stream.Collectors.toSet()), "dungeon");
        receivedStartingGear = magic.getBoolean("received_starting_gear");
        skulltulaTokens = magic.getInt("skulltula_tokens");
        skulltulaTrades = magic.getInt("skulltula_trades");
        knightsCrestsGiven = magic.getInt("knights_crests_given");
        orcaChoiceTier = Mth.clamp(magic.getInt("orca_choice_tier"), 0, 5);
        loadLevels(root.getList("skills", Tag.TAG_COMPOUND), skills, MAX_SKILLS, ZSSContentIds.SKILLS, "skill");
        loadIds(root.getList("disabled_skills", Tag.TAG_STRING), disabledSkills, MAX_SKILLS, ZSSContentIds.SKILLS, "skill");
        disabledSkills.remove(ZSSContentIds.BONUS_HEART);

        CompoundTag songData = root.getCompound("songs");
        loadIds(songData.getList("learned", Tag.TAG_STRING), songs, MAX_SONGS, ZSSContentIds.SONGS, "song");
        ListTag notes = songData.getList("scarecrow_notes", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(notes.size(), MAX_SCARECROW_NOTES); i++) {
            String note = notes.getString(i);
            if (note.length() <= 32) scarecrowNotes.add(note);
        }
        scarecrowConfirmedAt = Math.max(0L, songData.getLong("scarecrow_confirmed_at"));
        healingCooldownUntil = Math.max(0L, songData.getLong("healing_cooldown_until"));
        ListTag warps = songData.getList("warp_points", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(warps.size(), MAX_WARPS); i++) {
            CompoundTag entry = warps.getCompound(i);
            ResourceLocation id = parseId(entry.getString("id"));
            ResourceLocation dimension = parseId(entry.getString("dimension"));
            if (id != null && dimension != null) warpPoints.put(id, new WarpPoint(dimension, BlockPos.of(entry.getLong("pos"))));
        }
        horseUuid = songData.hasUUID("horse_uuid") ? songData.getUUID("horse_uuid") : null;
        horseChunk = songData.getLong("horse_chunk");
        ListTag healed = songData.getList("healed_npcs", Tag.TAG_STRING);
        for (int i = 0; i < Math.min(healed.size(), MAX_HEALED_NPCS); i++) {
            try {
                healedNpcs.add(UUID.fromString(healed.getString(i)));
            } catch (IllegalArgumentException exception) {
                ZeldaSwordSkills_Remastered.LOGGER.warn("Skipping invalid healed NPC UUID");
            }
        }

        ListTag questList = root.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(questList.size(), MAX_QUESTS); i++) {
            CompoundTag entry = questList.getCompound(i);
            ResourceLocation id = parseId(entry.getString("id"));
            ResourceLocation state = parseId(entry.getString("state"));
            if (id == null || state == null) continue;
            if (!ZSSContentIds.QUESTS.contains(id)) {
                ZeldaSwordSkills_Remastered.LOGGER.warn("Skipping unknown persisted quest ID: {}", id);
                continue;
            }
            Map<String, Integer> counters = new LinkedHashMap<>();
            CompoundTag counterTag = entry.getCompound("counters");
            counterTag.getAllKeys().stream().limit(32).filter(key -> key.length() <= 64)
                    .forEach(key -> counters.put(key, counterTag.getInt(key)));
            quests.put(id, new QuestProgress(state, Math.max(0, entry.getInt("step")), Math.max(0L, entry.getLong("updated_at")), Map.copyOf(counters)));
        }
        borrowedMask = parseId(root.getString("borrowed_mask"));
        if (root.contains("quest_escrow_ocarina", Tag.TAG_COMPOUND)) {
            escrowOcarina = ItemStack.of(root.getCompound("quest_escrow_ocarina"));
            if (!escrowOcarina.isEmpty()) escrowOcarina.setCount(1);
        }
        clampValues();
    }

    public void copyFrom(ZSSPlayerData source) {
        load(source.save());
    }

    public void resetLearnedSkills() {
        skills.keySet().removeIf(id -> !id.equals(ZSSContentIds.BONUS_HEART));
        combat.reset();
    }

    public boolean tryAcceptSkillIntent(long gameTime) {
        if (skillIntentWindowStart == Long.MIN_VALUE || gameTime - skillIntentWindowStart >= 20L || gameTime < skillIntentWindowStart) {
            skillIntentWindowStart = gameTime;
            skillIntentsInWindow = 0;
        }
        if (skillIntentsInWindow >= ZSSConfig.SERVER.intentsPerSecond.get()) return false;
        skillIntentsInWindow++;
        return true;
    }

    public void setMagic(float current, float maximum) {
        currentMagic = current;
        maxMagic = maximum;
        clampValues();
    }

    public boolean consumeMagic(float amount) {
        if (!Float.isFinite(amount) || amount < 0.0F || currentMagic < amount) return false;
        currentMagic -= amount;
        return true;
    }

    public boolean restoreMagic(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F || currentMagic >= maxMagic) return false;
        currentMagic = Math.min(maxMagic, currentMagic + amount);
        return true;
    }

    public boolean increaseMaxMagic(float amount) {
        if (!Float.isFinite(amount) || amount <= 0.0F) return false;
        float previous = maxMagic;
        maxMagic = Math.min(configuredMaximumMagic(), maxMagic + amount);
        if (maxMagic <= previous) return false;
        currentMagic = maxMagic;
        return true;
    }

    public boolean addSkulltulaToken() {
        return addSkulltulaTokens(1) > 0;
    }

    public int addSkulltulaTokens(int amount) {
        if (amount <= 0 || skulltulaTokens == Integer.MAX_VALUE) return 0;
        int added = Math.min(amount, Integer.MAX_VALUE - skulltulaTokens);
        skulltulaTokens += added;
        return added;
    }

    public boolean clearSkills() {
        if (skills.isEmpty()) return false;
        skills.clear();
        disabledSkills.clear();
        combat.reset();
        return true;
    }

    public float currentMagic() { return currentMagic; }
    public float maxMagic() { return maxMagic; }
    public int secretRooms() { return secretRooms; }
    public int discoverSecretRoom() { return ++secretRooms; }
    public int bossRooms() { return completedDungeons.size(); }
    public boolean hasCompletedDungeon(ResourceLocation id) { return completedDungeons.contains(id); }
    public int recordBossRoom(ResourceLocation id) {
        if (zeldaswordskills_remastered.worldgen.DungeonType.byId(id).isEmpty()) throw new IllegalArgumentException("Unknown dungeon: " + id);
        completedDungeons.add(id);
        return completedDungeons.size();
    }
    public boolean receivedStartingGear() { return receivedStartingGear; }
    public void markReceivedStartingGear() { receivedStartingGear = true; }
    public int skulltulaTokens() { return skulltulaTokens; }
    public int skulltulaTrades() { return skulltulaTrades; }
    public boolean advanceSkulltulaTrade() {
        if (skulltulaTrades >= skulltulaTokens / 10) return false;
        skulltulaTrades++;
        return true;
    }
    public int knightsCrestsGiven() { return knightsCrestsGiven; }
    public int orcaChoiceTier() { return orcaChoiceTier; }
    public void setOrcaChoiceTier(int tier) { orcaChoiceTier = Mth.clamp(tier, 0, 5); }
    public Map<ResourceLocation, Integer> skills() { return Map.copyOf(skills); }
    public Set<ResourceLocation> songs() { return Set.copyOf(songs); }
    public List<String> scarecrowNotes() { return List.copyOf(scarecrowNotes); }
    public long scarecrowConfirmedAt() { return scarecrowConfirmedAt; }
    public Map<ResourceLocation, WarpPoint> warpPoints() { return Map.copyOf(warpPoints); }
    public long healingCooldownUntil() { return healingCooldownUntil; }
    public Optional<UUID> horseUuid() { return Optional.ofNullable(horseUuid); }
    public long horseChunk() { return horseChunk; }
    public Set<UUID> healedNpcs() { return Set.copyOf(healedNpcs); }
    public Map<ResourceLocation, QuestProgress> quests() { return Map.copyOf(quests); }
    public Optional<ResourceLocation> borrowedMask() { return Optional.ofNullable(borrowedMask); }
    public ItemStack escrowOcarina() { return escrowOcarina.copy(); }
    public PlayerCombatState combat() { return combat; }
    public PlayerSongState songState() { return songState; }

    public boolean learnSong(ResourceLocation id) {
        if (!ZSSContentIds.SONGS.contains(id) || songs.size() >= MAX_SONGS && !songs.contains(id)) return false;
        return songs.add(id);
    }

    public boolean forgetSong(ResourceLocation id) {
        return songs.remove(id);
    }

    public void setScarecrowMelody(List<SongNote> notes, long confirmationTime) {
        if (notes.size() != MAX_SCARECROW_NOTES) throw new IllegalArgumentException("Scarecrow melody must contain eight notes");
        scarecrowNotes.clear();
        notes.forEach(note -> scarecrowNotes.add(note.name()));
        scarecrowConfirmedAt = Math.max(0L, confirmationTime);
    }

    public void clearScarecrowMelody() {
        scarecrowNotes.clear();
        scarecrowConfirmedAt = 0L;
        songs.remove(ZSSContentIds.SCARECROW);
    }

    public void setWarpPoint(ResourceLocation song, ResourceLocation dimension, BlockPos pos) {
        if (!ZSSContentIds.SONGS.contains(song)) throw new IllegalArgumentException("Unknown warp song: " + song);
        if (warpPoints.size() >= MAX_WARPS && !warpPoints.containsKey(song)) throw new IllegalStateException("Warp point limit reached");
        warpPoints.put(song, new WarpPoint(dimension, pos.immutable()));
    }

    public void setHealingCooldownUntil(long gameTime) { healingCooldownUntil = Math.max(0L, gameTime); }

    public void setHorse(UUID uuid, long chunk) {
        horseUuid = uuid;
        horseChunk = chunk;
    }

    public void setQuestProgress(ResourceLocation id, ResourceLocation state, int step, long updatedAt,
                                 Map<String, Integer> counters) {
        if (!ZSSContentIds.QUESTS.contains(id)) throw new IllegalArgumentException("Unknown quest ID: " + id);
        if (quests.size() >= MAX_QUESTS && !quests.containsKey(id)) throw new IllegalStateException("Quest limit reached");
        Map<String, Integer> safeCounters = new LinkedHashMap<>();
        counters.entrySet().stream().limit(32)
                .filter(entry -> entry.getKey() != null && entry.getKey().length() <= 64)
                .forEach(entry -> safeCounters.put(entry.getKey(), Math.max(0, entry.getValue())));
        quests.put(id, new QuestProgress(state, Math.max(0, step), Math.max(0L, updatedAt), Map.copyOf(safeCounters)));
    }

    public void setBorrowedMask(ResourceLocation id) {
        borrowedMask = id;
    }

    public void setEscrowOcarina(ItemStack stack) {
        escrowOcarina = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    public boolean giveKnightsCrest() {
        if (knightsCrestsGiven >= 100) return false;
        knightsCrestsGiven++;
        return true;
    }

    public boolean markNpcHealed(UUID uuid) {
        return healedNpcs.size() < MAX_HEALED_NPCS && healedNpcs.add(uuid);
    }

    public ItemStack takeEscrowOcarina() {
        ItemStack stack = escrowOcarina;
        escrowOcarina = ItemStack.EMPTY;
        return stack;
    }

    public void setSkillLevel(ResourceLocation id, int level) {
        if (!ZSSContentIds.SKILLS.contains(id)) throw new IllegalArgumentException("Unknown skill ID: " + id);
        int maximum = id.equals(ZSSContentIds.SWORD_BASIC) ? 10
                : id.equals(ZSSContentIds.CONTINUOUS_FLASH) ? 5
                : id.equals(ZSSContentIds.BONUS_HEART) ? ZSSConfig.SERVER.maximumHeartContainers.get() : 5;
        if (level <= 0 || maximum == 0) skills.remove(id);
        else if (skills.size() < MAX_SKILLS || skills.containsKey(id)) skills.put(id, Mth.clamp(level, 1, maximum));
    }

    public int skillLevel(ResourceLocation id) { return skills.getOrDefault(id, 0); }

    public boolean skillEnabled(ResourceLocation id) { return !disabledSkills.contains(id); }

    public int activeSkillLevel(ResourceLocation id) { return skillEnabled(id) ? skillLevel(id) : 0; }

    public boolean setSkillEnabled(ResourceLocation id, boolean enabled) {
        if (!ZSSContentIds.SKILLS.contains(id) || id.equals(ZSSContentIds.BONUS_HEART) || skillLevel(id) <= 0) return false;
        return enabled ? disabledSkills.remove(id) : disabledSkills.add(id);
    }

    public int skillMaximum(ResourceLocation id) {
        return id.equals(ZSSContentIds.SWORD_BASIC) ? 10
                : id.equals(ZSSContentIds.CONTINUOUS_FLASH) ? 5
                : id.equals(ZSSContentIds.BONUS_HEART) ? ZSSConfig.SERVER.maximumHeartContainers.get() : 5;
    }

    public boolean allSkillTypesLearned() {
        for (ResourceLocation id : ZSSContentIds.SKILLS) {
            if (!id.equals(ZSSContentIds.BONUS_HEART) && skillLevel(id) <= 0) return false;
        }
        return true;
    }

    private void clampValues() {
        maxMagic = Mth.clamp(Float.isFinite(maxMagic) ? maxMagic : 0.0F, 0.0F, configuredMaximumMagic());
        currentMagic = Mth.clamp(Float.isFinite(currentMagic) ? currentMagic : 0.0F, 0.0F, maxMagic);
        secretRooms = Math.max(0, secretRooms);
        skulltulaTokens = Math.max(0, skulltulaTokens);
        skulltulaTrades = Mth.clamp(skulltulaTrades, 0, skulltulaTokens / 10);
        knightsCrestsGiven = Mth.clamp(knightsCrestsGiven, 0, 100);
    }

    private static float configuredMaximumMagic() {
        return ZSSConfig.SERVER.maximumMagic.get().floatValue();
    }

    private static ListTag saveLevels(Map<ResourceLocation, Integer> values) {
        ListTag list = new ListTag();
        values.forEach((id, level) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", id.toString());
            entry.putInt("level", level);
            list.add(entry);
        });
        return list;
    }

    private static ListTag saveIds(Set<ResourceLocation> values) {
        ListTag list = new ListTag();
        values.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        return list;
    }

    private static void loadLevels(ListTag list, Map<ResourceLocation, Integer> output, int maximum,
                                   Set<ResourceLocation> known, String type) {
        for (int i = 0; i < Math.min(list.size(), maximum); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = parseId(entry.getString("id"));
            int level = entry.getInt("level");
            if (id != null && !known.contains(id)) {
                ZeldaSwordSkills_Remastered.LOGGER.warn("Skipping unknown persisted {} ID: {}", type, id);
            } else if (id != null && level > 0) {
                int cap = id.equals(ZSSContentIds.SWORD_BASIC) ? 10
                        : id.equals(ZSSContentIds.CONTINUOUS_FLASH) ? 5
                : id.equals(ZSSContentIds.BONUS_HEART) ? ZSSConfig.SERVER.maximumHeartContainers.get() : 5;
                if (cap > 0) output.put(id, Mth.clamp(level, 1, cap));
            }
        }
    }

    private static void loadIds(ListTag list, Set<ResourceLocation> output, int maximum,
                                Set<ResourceLocation> known, String type) {
        for (int i = 0; i < Math.min(list.size(), maximum); i++) {
            ResourceLocation id = parseId(list.getString(i));
            if (id != null && !known.contains(id)) {
                ZeldaSwordSkills_Remastered.LOGGER.warn("Skipping unknown persisted {} ID: {}", type, id);
            } else if (id != null) {
                output.add(id);
            }
        }
    }

    private static ResourceLocation parseId(String value) {
        if (value == null || value.isBlank() || value.length() > 128) return null;
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) ZeldaSwordSkills_Remastered.LOGGER.warn("Skipping invalid persisted resource ID: {}", value);
        return id;
    }

    public record WarpPoint(ResourceLocation dimension, BlockPos pos) {}
    public record QuestProgress(ResourceLocation state, int step, long updatedAt, Map<String, Integer> counters) {}
}
