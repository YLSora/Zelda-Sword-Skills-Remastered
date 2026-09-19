package zeldaswordskills_remastered.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import zeldaswordskills_remastered.block.CeramicJarBlock;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.SongCatalog;
import zeldaswordskills_remastered.worldgen.DungeonController;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.Optional;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class StageNineBlockEntities {
    private StageNineBlockEntities() {}

    public abstract static class Synced extends BlockEntity {
        protected Synced(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
            super(type, pos, state);
        }
        protected final void sync() {
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
        @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
        @Override public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
            CompoundTag tag = packet.getTag();
            if (tag != null) load(tag);
        }
    }

    public static final class Storage extends net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity {
        private net.minecraft.core.NonNullList<ItemStack> items;

        public Storage(BlockPos pos, BlockState state) {
            super(ZSSRegistries.STORAGE_BLOCK_ENTITY.get(), pos, state);
            items = net.minecraft.core.NonNullList.withSize(state.getBlock() instanceof CeramicJarBlock ? 1 : 27, ItemStack.EMPTY);
        }

        public ItemStack takeJarContents() { return removeItemNoUpdate(0); }
        public void setJarContents(ItemStack stack) { setItem(0, stack); }
        public void dropContents() {
            if (!(level instanceof ServerLevel)) return;
            for (int slot = 0; slot < getContainerSize(); slot++) {
                ItemStack stack = removeItemNoUpdate(slot);
                if (!stack.isEmpty()) net.minecraft.world.Containers.dropItemStack(level,
                        worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5, stack);
            }
        }

        @Override protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (!trySaveLootTable(tag)) net.minecraft.world.ContainerHelper.saveAllItems(tag, items);
        }
        @Override public void load(CompoundTag tag) {
            super.load(tag);
            items = net.minecraft.core.NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
            lootTable = null;
            lootTableSeed = 0L;
            if (!tryLoadLootTable(tag)) net.minecraft.world.ContainerHelper.loadAllItems(tag, items);
        }
        @Override protected Component getDefaultName() { return Component.translatable("container.zeldaswordskills_remastered.locked_chest"); }
        @Nullable @Override protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
            return getContainerSize() == 27 ? ChestMenu.threeRows(id, playerInventory, this) : null;
        }
        @Override public int getContainerSize() { return items.size(); }
        @Override protected net.minecraft.core.NonNullList<ItemStack> getItems() { return items; }
        @Override protected void setItems(net.minecraft.core.NonNullList<ItemStack> value) { items = value; }
        @Override public void unpackLootTable(@Nullable Player player) {
            if (!(level instanceof ServerLevel) || lootTable == null) return;
            super.unpackLootTable(player);
            setChanged();
        }
        @Override public boolean stillValid(Player player) {
            return level != null && level.getBlockEntity(worldPosition) == this
                    && player.distanceToSqr(worldPosition.getX() + .5, worldPosition.getY() + .5, worldPosition.getZ() + .5) <= 64;
        }
        @Override public void clearContent() {
            lootTable = null;
            lootTableSeed = 0L;
            super.clearContent();
            setChanged();
        }
    }

    public static final class DungeonCore extends Synced {
        private DungeonType dungeonType;
        private final Set<UUID> bossUuids = new LinkedHashSet<>();
        private boolean completed;
        private int arenaRadius;
        private int arenaWidth;
        private int arenaHeight;
        private int doorOffsetY = 1;
        private int doorDistance;
        private Direction doorSide;
        private long nextHazardTick = -1L;
        private long fireBattleTicks;
        private int fireBattleDifficulty;
        private final Set<UUID> fireReinforcements = new LinkedHashSet<>();
        private int forestDifficulty;
        private int forestHazardDelay;
        private final Set<UUID> forestReinforcements = new LinkedHashSet<>();
        private int forestReinforcementDelay;
        private boolean forestCancelling;
        private int iceDifficulty;
        private boolean iceCancelling;
        private final Set<UUID> iceReinforcements = new LinkedHashSet<>();
        private int iceReinforcementDelay;
        private int waterDifficulty;
        private int waterHazardDelay;
        private boolean waterCancelling;
        private UUID waterReinforcement;
        private int desertDifficulty;
        private int desertHazardDelay;
        private boolean desertCancelling;
        private final Set<UUID> desertReinforcements = new LinkedHashSet<>();

        public DungeonCore(BlockPos pos, BlockState state) { super(ZSSRegistries.DUNGEON_CORE_BLOCK_ENTITY.get(), pos, state); }
        public Optional<DungeonType> dungeonType() { return Optional.ofNullable(dungeonType); }
        public Optional<ResourceLocation> dungeonId() { return dungeonType().map(DungeonType::id); }
        public Set<UUID> bossUuids() { return Set.copyOf(bossUuids); }
        public boolean completed() { return completed; }
        public int arenaRadius() { return arenaRadius; }
        public int arenaWidth() { return arenaWidth; }
        public int desertDifficulty() { return desertDifficulty; }
        public int desertHazardDelay() { return desertHazardDelay; }
        public boolean desertCancelling() { return desertCancelling; }
        public Set<UUID> desertReinforcements() { return Set.copyOf(desertReinforcements); }
        public void addDesertReinforcement(UUID uuid) { desertReinforcements.add(uuid); setChanged(); }
        public void removeDesertReinforcement(UUID uuid) { desertReinforcements.remove(uuid); setChanged(); }
        public void beginDesertBattle(int difficulty, int delay) {
            desertDifficulty = difficulty; desertHazardDelay = delay; desertCancelling = false; setChanged();
        }
        public void setDesertHazardDelay(int delay) { desertHazardDelay = delay; setChanged(); }
        public void cancelDesertBattle() { desertCancelling = true; setChanged(); }
        public void resetDesertBattle() {
            desertDifficulty = 0; desertHazardDelay = 0; desertCancelling = false;
            desertReinforcements.clear(); clearBosses(); setChanged();
        }
        public int waterDifficulty() { return waterDifficulty; }
        public int waterHazardDelay() { return waterHazardDelay; }
        public boolean waterCancelling() { return waterCancelling; }
        public Optional<UUID> waterReinforcement() { return Optional.ofNullable(waterReinforcement); }
        public void setWaterReinforcement(UUID uuid) { waterReinforcement = uuid; setChanged(); }
        public void removeWaterReinforcement(UUID uuid) {
            if (uuid.equals(waterReinforcement)) { waterReinforcement = null; setChanged(); }
        }
        public void beginWaterBattle(int difficulty) {
            waterDifficulty = difficulty; waterHazardDelay = 4800 - 600 * difficulty;
            waterCancelling = false; setChanged();
        }
        public void setWaterHazardDelay(int delay) { waterHazardDelay = delay; setChanged(); }
        public void cancelWaterBattle() { waterCancelling = true; setChanged(); }
        public void resetWaterBattle() {
            waterDifficulty = 0; waterHazardDelay = 0; waterCancelling = false; waterReinforcement = null;
            clearBosses(); setChanged();
        }
        public int iceDifficulty() { return iceDifficulty; }
        public boolean iceCancelling() { return iceCancelling; }
        public Set<UUID> iceReinforcements() { return Set.copyOf(iceReinforcements); }
        public void addIceReinforcement(UUID uuid) { iceReinforcements.add(uuid); setChanged(); }
        public void removeIceReinforcement(UUID uuid) { iceReinforcements.remove(uuid); setChanged(); }
        public int iceReinforcementDelay() { return iceReinforcementDelay; }
        public void setIceReinforcementDelay(int delay) { iceReinforcementDelay = delay; setChanged(); }
        public void beginIceBattle(int difficulty) { iceDifficulty = difficulty; iceCancelling = false; setChanged(); }
        public void cancelIceBattle() { iceCancelling = true; setChanged(); }
        public void resetIceBattle() {
            iceDifficulty = 0; iceCancelling = false; iceReinforcements.clear(); iceReinforcementDelay = 0;
            clearBosses(); setChanged();
        }
        public int forestDifficulty() { return forestDifficulty; }
        public int forestHazardDelay() { return forestHazardDelay; }
        public Set<UUID> forestReinforcements() { return Set.copyOf(forestReinforcements); }
        public int forestReinforcementDelay() { return forestReinforcementDelay; }
        public void setForestReinforcementDelay(int delay) { forestReinforcementDelay = delay; setChanged(); }
        public void addForestReinforcement(UUID uuid) { forestReinforcements.add(uuid); setChanged(); }
        public void removeForestReinforcement(UUID uuid) { forestReinforcements.remove(uuid); setChanged(); }
        public boolean forestCancelling() { return forestCancelling; }
        public void beginForestBattle(int difficulty, int delay) {
            forestDifficulty = difficulty; forestHazardDelay = delay; forestCancelling = false; setChanged();
        }
        public void setForestHazardDelay(int delay) { forestHazardDelay = delay; setChanged(); }
        public void cancelForestBattle() { forestCancelling = true; setChanged(); }
        public void resetForestBattle() {
            forestDifficulty = 0; forestHazardDelay = 0; forestReinforcements.clear(); forestReinforcementDelay = 0;
            forestCancelling = false; clearBosses(); setChanged();
        }
        public int arenaHeight() { return arenaHeight; }
        public int doorOffsetY() { return doorOffsetY; }
        public int doorDistance() { return doorDistance; }
        public net.minecraft.world.level.block.Rotation structureRotation() {
            return getBlockState().getValue(zeldaswordskills_remastered.block.DungeonBlocks.Core.STRUCTURE_ROTATION);
        }
        public Optional<Direction> doorSide() { return Optional.ofNullable(doorSide).map(structureRotation()::rotate); }
        public long nextHazardTick() { return nextHazardTick; }
        public long fireBattleTicks() { return fireBattleTicks; }
        public int fireBattleDifficulty() { return fireBattleDifficulty; }
        public Set<UUID> fireReinforcements() { return Set.copyOf(fireReinforcements); }
        public void beginFireBattle(int difficulty) {
            fireBattleTicks = 0L; fireBattleDifficulty = difficulty; fireReinforcements.clear(); setChanged();
        }
        public void advanceFireBattle() { ++fireBattleTicks; setChanged(); }
        public void addFireReinforcement(UUID uuid) { fireReinforcements.add(uuid); setChanged(); }
        public void removeFireReinforcement(UUID uuid) { fireReinforcements.remove(uuid); setChanged(); }
        public void resetFireBattle() {
            fireBattleTicks = 0L; fireBattleDifficulty = 0; fireReinforcements.clear(); clearBosses(); setChanged();
        }
        public boolean setDungeonType(DungeonType type) {
            if (type == null || dungeonType != null && dungeonType != type) return false;
            dungeonType = type; sync(); return true;
        }
        public void setBosses(java.util.Collection<UUID> uuids) {
            bossUuids.clear();
            uuids.stream().limit(8).forEach(bossUuids::add);
            sync();
        }
        public void removeBoss(UUID uuid) { if (bossUuids.remove(uuid)) sync(); }
        public void clearBosses() { if (!bossUuids.isEmpty()) { bossUuids.clear(); sync(); } }
        public void setNextHazardTick(long tick) { nextHazardTick = Math.max(-1L, tick); sync(); }
        public void complete() { completed = true; bossUuids.clear(); nextHazardTick = -1L; sync(); }
        public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, DungeonCore core) {
            if (level instanceof ServerLevel server) DungeonController.tick(server, core);
        }
        @Override protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag);
            if (dungeonType != null) tag.putString("dungeon_id", dungeonType.id().toString());
            net.minecraft.nbt.ListTag bosses = new net.minecraft.nbt.ListTag();
            bossUuids.forEach(uuid -> bosses.add(net.minecraft.nbt.NbtUtils.createUUID(uuid)));
            tag.put("boss_uuids", bosses);
            tag.putBoolean("completed", completed);
            tag.putInt("arena_radius", arenaRadius);
            tag.putInt("arena_width", arenaWidth);
            tag.putInt("arena_height", arenaHeight);
            tag.putInt("door_offset_y", doorOffsetY);
            tag.putInt("door_distance", doorDistance);
            if (doorSide != null) tag.putString("door_side", doorSide.getName());
            tag.putLong("next_hazard_tick", nextHazardTick);
            tag.putLong("fire_battle_ticks", fireBattleTicks);
            tag.putInt("fire_battle_difficulty", fireBattleDifficulty);
            net.minecraft.nbt.ListTag reinforcements = new net.minecraft.nbt.ListTag();
            fireReinforcements.forEach(uuid -> reinforcements.add(net.minecraft.nbt.NbtUtils.createUUID(uuid)));
            tag.put("fire_reinforcements", reinforcements);
            tag.putInt("forest_difficulty", forestDifficulty);
            tag.putInt("forest_hazard_delay", forestHazardDelay);
            net.minecraft.nbt.ListTag forestMobs = new net.minecraft.nbt.ListTag();
            forestReinforcements.forEach(uuid -> forestMobs.add(net.minecraft.nbt.NbtUtils.createUUID(uuid)));
            tag.put("forest_reinforcements", forestMobs);
            tag.putInt("forest_reinforcement_delay", forestReinforcementDelay);
            tag.putBoolean("forest_cancelling", forestCancelling);
            tag.putInt("ice_difficulty", iceDifficulty);
            tag.putBoolean("ice_cancelling", iceCancelling);
            net.minecraft.nbt.ListTag iceMobs = new net.minecraft.nbt.ListTag();
            iceReinforcements.forEach(uuid -> iceMobs.add(net.minecraft.nbt.NbtUtils.createUUID(uuid)));
            tag.put("ice_reinforcements", iceMobs);
            tag.putInt("ice_reinforcement_delay", iceReinforcementDelay);
            tag.putInt("water_difficulty", waterDifficulty);
            tag.putInt("water_hazard_delay", waterHazardDelay);
            tag.putBoolean("water_cancelling", waterCancelling);
            if (waterReinforcement != null) tag.putUUID("water_reinforcement", waterReinforcement);
            tag.putInt("desert_difficulty", desertDifficulty);
            tag.putInt("desert_hazard_delay", desertHazardDelay);
            tag.putBoolean("desert_cancelling", desertCancelling);
            net.minecraft.nbt.ListTag desertMobs = new net.minecraft.nbt.ListTag();
            desertReinforcements.forEach(uuid -> desertMobs.add(net.minecraft.nbt.NbtUtils.createUUID(uuid)));
            tag.put("desert_reinforcements", desertMobs);
        }
        @Override public void load(CompoundTag tag) {
            super.load(tag);
            ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("dungeon_id"));
            dungeonType = parsed == null ? null : DungeonType.byId(parsed).orElse(null);
            bossUuids.clear();
            net.minecraft.nbt.ListTag bosses = tag.getList("boss_uuids", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
            for (int index = 0; index < Math.min(8, bosses.size()); index++) {
                try { bossUuids.add(net.minecraft.nbt.NbtUtils.loadUUID(bosses.get(index))); }
                catch (IllegalArgumentException ignored) { }
            }
            completed = tag.getBoolean("completed");
            arenaRadius = Math.max(0, Math.min(21, tag.getInt("arena_radius")));
            arenaWidth = Math.max(0, Math.min(43, tag.getInt("arena_width")));
            arenaHeight = Math.max(0, Math.min(32, tag.getInt("arena_height")));
            doorOffsetY = Math.max(1, Math.min(3, tag.getInt("door_offset_y")));
            doorDistance = Math.max(0, Math.min(22, tag.getInt("door_distance")));
            doorSide = Direction.byName(tag.getString("door_side"));
            if (doorSide != null && !doorSide.getAxis().isHorizontal()) doorSide = null;
            nextHazardTick = tag.contains("next_hazard_tick", net.minecraft.nbt.Tag.TAG_LONG)
                    ? Math.max(-1L, tag.getLong("next_hazard_tick")) : -1L;
            fireBattleTicks = Math.max(0L, tag.getLong("fire_battle_ticks"));
            fireBattleDifficulty = Math.max(0, Math.min(3, tag.getInt("fire_battle_difficulty")));
            forestDifficulty = Math.max(0, Math.min(3, tag.getInt("forest_difficulty")));
            forestHazardDelay = Math.max(0, Math.min(599, tag.getInt("forest_hazard_delay")));
            forestReinforcements.clear();
            for (net.minecraft.nbt.Tag mob : tag.getList("forest_reinforcements", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                try { forestReinforcements.add(net.minecraft.nbt.NbtUtils.loadUUID(mob)); }
                catch (IllegalArgumentException ignored) { }
            }
            forestReinforcementDelay = Math.max(0, Math.min(599, tag.getInt("forest_reinforcement_delay")));
            forestCancelling = tag.getBoolean("forest_cancelling");
            iceDifficulty = Math.max(0, Math.min(3, tag.getInt("ice_difficulty")));
            waterDifficulty = Math.max(0, Math.min(3, tag.getInt("water_difficulty")));
            waterHazardDelay = Math.max(0, Math.min(4200, tag.getInt("water_hazard_delay")));
            waterCancelling = tag.getBoolean("water_cancelling");
            waterReinforcement = tag.hasUUID("water_reinforcement") ? tag.getUUID("water_reinforcement") : null;
            desertDifficulty = Math.max(0, Math.min(3, tag.getInt("desert_difficulty")));
            desertHazardDelay = Math.max(0, Math.min(300, tag.getInt("desert_hazard_delay")));
            desertCancelling = tag.getBoolean("desert_cancelling");
            desertReinforcements.clear();
            for (net.minecraft.nbt.Tag mob : tag.getList("desert_reinforcements", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                try { desertReinforcements.add(net.minecraft.nbt.NbtUtils.loadUUID(mob)); }
                catch (IllegalArgumentException ignored) { }
            }
            iceCancelling = tag.getBoolean("ice_cancelling");
            iceReinforcementDelay = Math.max(0, Math.min(599, tag.getInt("ice_reinforcement_delay")));
            iceReinforcements.clear();
            for (net.minecraft.nbt.Tag mob : tag.getList("ice_reinforcements", net.minecraft.nbt.Tag.TAG_INT_ARRAY)) {
                try { iceReinforcements.add(net.minecraft.nbt.NbtUtils.loadUUID(mob)); }
                catch (IllegalArgumentException ignored) { }
            }
            fireReinforcements.clear();
            net.minecraft.nbt.ListTag reinforcements = tag.getList("fire_reinforcements", net.minecraft.nbt.Tag.TAG_INT_ARRAY);
            for (net.minecraft.nbt.Tag reinforcement : reinforcements) {
                try { fireReinforcements.add(net.minecraft.nbt.NbtUtils.loadUUID(reinforcement)); }
                catch (IllegalArgumentException ignored) { }
            }
        }
    }

    public static final class GossipStone extends Synced {
        public static final int MAX_MESSAGE_LENGTH = 192;
        private String message = "";
        private UUID owner;
        private long nextFairySpawn;
        public GossipStone(BlockPos pos, BlockState state) { super(ZSSRegistries.GOSSIP_STONE_BLOCK_ENTITY.get(), pos, state); }
        public String message() { return message.isBlank() ? "message.zeldaswordskills_remastered.gossip.default" : message; }
        public Optional<UUID> owner() { return Optional.ofNullable(owner); }
        public long nextFairySpawn() { return nextFairySpawn; }
        public boolean setMessage(Player editor, String value) {
            if (value == null || value.length() > MAX_MESSAGE_LENGTH || !mayEdit(editor)) return false;
            if (owner == null) owner = editor.getUUID();
            message = value; sync(); return true;
        }
        public boolean mayEdit(Player player) { return player.hasPermissions(2) || owner == null || owner.equals(player.getUUID()); }
        public void setNextFairySpawn(long gameTime) { nextFairySpawn = Math.max(0, gameTime); sync(); }
        @Override protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag); tag.putString("message", message); tag.putLong("next_fairy_spawn", nextFairySpawn);
            if (owner != null) tag.putUUID("owner", owner);
        }
        @Override public void load(CompoundTag tag) {
            super.load(tag); message = tag.getString("message"); nextFairySpawn = Math.max(0, tag.getLong("next_fairy_spawn"));
            owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        }
    }

    public static final class Inscription extends Synced {
        private ResourceLocation songId = ZSSContentIds.TIME;
        private UUID owner;
        public Inscription(BlockPos pos, BlockState state) { super(ZSSRegistries.INSCRIPTION_BLOCK_ENTITY.get(), pos, state); }
        public ResourceLocation songId() { return songId; }
        public boolean setSong(Player editor, ResourceLocation song) {
            if (SongCatalog.get(song).isEmpty() || !mayEdit(editor)) return false;
            if (owner == null) owner = editor.getUUID();
            songId = song; sync(); return true;
        }
        public boolean mayEdit(Player player) { return player.hasPermissions(2) || owner == null || owner.equals(player.getUUID()); }
        @Override protected void saveAdditional(CompoundTag tag) {
            super.saveAdditional(tag); tag.putString("song", songId.toString()); if (owner != null) tag.putUUID("owner", owner);
        }
        @Override public void load(CompoundTag tag) {
            super.load(tag); ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("song"));
            songId = parsed != null && SongCatalog.get(parsed).isPresent() ? parsed : ZSSContentIds.TIME;
            owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        }
    }

    public static final class SacredFlame extends Synced {
        private long resetAt;
        public SacredFlame(BlockPos pos, BlockState state) { super(ZSSRegistries.SACRED_FLAME_BLOCK_ENTITY.get(), pos, state); }
        public long resetAt() { return resetAt; }
        public void extinguish(long time) { resetAt = Math.max(0, time); sync(); }
        public void clearReset() { resetAt = 0; sync(); }
        @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putLong("reset_at", resetAt); }
        @Override public void load(CompoundTag tag) { super.load(tag); resetAt = Math.max(0, tag.getLong("reset_at")); }
    }
}
