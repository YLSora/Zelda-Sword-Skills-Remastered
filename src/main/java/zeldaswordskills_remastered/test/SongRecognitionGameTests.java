package zeldaswordskills_remastered.test;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.PlayLevelSoundEvent;
import net.minecraftforge.gametest.GameTestHolder;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.block.WarpStoneBlock;
import zeldaswordskills_remastered.capability.ZSSCapabilities;
import zeldaswordskills_remastered.capability.ZSSPlayerData;
import zeldaswordskills_remastered.network.SongIntentMessage;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.song.SongCatalog;
import zeldaswordskills_remastered.song.SongNote;
import zeldaswordskills_remastered.song.SongService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder(ZeldaSwordSkills_Remastered.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class SongRecognitionGameTests {
    private SongRecognitionGameTests() {}

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", timeoutTicks = 180)
    public static void freePlaySkipsRecognitionAndResumesWithFreshNotes(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_FreePlay]"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.OCARINA_OF_TIME.get()));
        player.setHealth(player.getMaxHealth() - 5.0F);
        ZSSPlayerData data = new ZSSPlayerData();
        data.learnSong(ZSSContentIds.HEALING);
        var song = SongCatalog.get(ZSSContentIds.HEALING).orElseThrow();
        SongService.begin(player, data, false);
        SongService.note(player, data, song.notes().get(0), 0);
        SongService.setFreePlay(player, data, true);
        helper.assertTrue(data.songState().notes().isEmpty() && data.songState().hudNotes().isEmpty(),
                "Enabling free play did not clear the previous melody");
        for (int repeat = 0; repeat < 3; repeat++) {
            song.notes().forEach(note -> SongService.note(player, data, note, 1));
        }
        helper.assertTrue(data.songState().freePlay() && data.songState().notes().isEmpty()
                        && !data.songState().hudNotes().isEmpty() && data.songState().hudNotes().size() <= 8
                        && data.songState().matchedSong() == null && data.songState().recognitionDeadline() == 0L,
                "Free play accumulated recognition state or stopped accepting notes");
        helper.runAtTickTime(160L, () -> {
            SongService.tick(player, data);
            helper.assertTrue(data.songState().open() && data.songState().freePlay()
                            && player.getHealth() == player.getMaxHealth() - 5.0F && data.healingCooldownUntil() == 0L,
                    "Free play timed out or executed a song effect");
            SongService.setFreePlay(player, data, false);
            helper.assertTrue(!data.songState().freePlay() && data.songState().hudNotes().isEmpty(),
                    "Disabling free play retained old notes");
            song.notes().forEach(note -> SongService.note(player, data, note, -1));
            helper.assertTrue(song.id().equals(data.songState().matchedSong()),
                    "Recognition did not resume after disabling free play");
            SongService.setFreePlay(player, data, true);
            helper.assertTrue(!data.songState().freePlay() && song.id().equals(data.songState().matchedSong()),
                    "Mode switch interrupted an already recognized song");
            SongService.cancel(player, data);
            SongService.setFreePlay(player, data, true);
            helper.assertTrue(!data.songState().open() && !data.songState().freePlay(),
                    "Mode switch reopened a closed session");
            SongService.begin(player, data, false);
            helper.assertTrue(!data.songState().freePlay(), "New session did not default to recognition");
            SongService.cancel(player, data);
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void freePlayDoesNotRecordScarecrowMelodies(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_FreeRecord]"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.FAIRY_OCARINA.get()));
        ZSSPlayerData data = new ZSSPlayerData();
        data.songState().open(ZSSRegistries.FAIRY_OCARINA.getId(), true);
        SongService.setFreePlay(player, data, true);
        List<SongNote> custom = List.of(SongNote.D1, SongNote.D2, SongNote.D1, SongNote.D2,
                SongNote.F1, SongNote.A2, SongNote.B2, SongNote.F1);
        custom.forEach(note -> SongService.note(player, data, note, 0));
        helper.assertTrue(data.scarecrowNotes().isEmpty() && data.songState().open(),
                "Free play recorded a scarecrow melody");
        SongService.setFreePlay(player, data, false);
        custom.forEach(note -> SongService.note(player, data, note, 0));
        helper.assertTrue(data.scarecrowNotes().equals(custom.stream().map(Enum::name).toList()),
                "Scarecrow recording did not resume after free play");
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void semitoneOffsetsChangeSoundWithoutChangingMelodies(GameTestHelper helper) {
        Map<ResourceLocation, List<SongNote>> melodies = new LinkedHashMap<>();
        SongCatalog.all().forEach((id, song) -> melodies.put(id, song.notes()));
        List<SongNote> custom = List.of(SongNote.D1, SongNote.D2, SongNote.D1, SongNote.D2,
                SongNote.F1, SongNote.A2, SongNote.B2, SongNote.F1);
        melodies.put(ZSSContentIds.SCARECROW, custom);
        List<Float> pitches = new ArrayList<>();
        Consumer<PlayLevelSoundEvent.AtPosition> capture = event -> {
            if (event.getLevel() == helper.getLevel() && event.getSound() != null
                    && event.getSound().value() == ZSSRegistries.NOTE_OCARINA.get()) {
                pitches.add(event.getOriginalPitch());
            }
        };
        MinecraftForge.EVENT_BUS.addListener(capture);
        try {
            for (var instrument : List.of(ZSSRegistries.FAIRY_OCARINA.get(), ZSSRegistries.OCARINA_OF_TIME.get(),
                    ZSSRegistries.GODDESS_HARP.get())) {
                FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Pitch]"));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(instrument));
                ZSSPlayerData data = new ZSSPlayerData();
                ZSSContentIds.SONGS.forEach(data::learnSong);
                data.setScarecrowMelody(custom, 0L);
                for (var melody : melodies.entrySet()) {
                    SongService.begin(player, data, false);
                    int index = 0;
                    for (SongNote note : melody.getValue()) {
                        int offset = new int[]{1, 0, -1, 0}[index++ % 4];
                        pitches.clear();
                        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                        try {
                            SongIntentMessage.encode(new SongIntentMessage(SongIntentMessage.Action.NOTE, Optional.of(note), offset), buffer);
                            SongIntentMessage decoded = SongIntentMessage.decode(buffer);
                            SongService.note(player, data, decoded.note().orElseThrow(), decoded.semitoneOffset());
                        } finally {
                            buffer.release();
                        }
                        float ratio = offset == 1 ? 1.0594631F : offset == -1 ? 0.9438743F : 1.0F;
                        helper.assertTrue(pitches.size() == 1 && Math.abs(pitches.get(0) - note.pitch() * ratio) < 0.000001F,
                                "Wrong emitted pitch after offset/release: " + melody.getKey() + "/" + note + "/" + offset);
                    }
                    helper.assertTrue(melody.getKey().equals(data.songState().matchedSong())
                                    && melody.getValue().equals(data.songState().notes()),
                            "Pitch adjustment changed recognition or stored notes: " + melody.getKey());
                    SongService.cancel(player, data);
                }
            }
        } finally {
            MinecraftForge.EVENT_BUS.unregister(capture);
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void songPacketsValidateSemitoneOffsets(GameTestHelper helper) {
        for (var action : SongIntentMessage.Action.values()) {
            for (int offset = -1; offset <= 1; offset++) {
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    SongIntentMessage message = action == SongIntentMessage.Action.NOTE
                            ? new SongIntentMessage(action, Optional.of(SongNote.D2), offset) : new SongIntentMessage(action);
                    SongIntentMessage.encode(message, buffer);
                    helper.assertTrue(message.equals(SongIntentMessage.decode(buffer)) && !buffer.isReadable(),
                            "Song packet did not round-trip: " + action + "/" + offset);
                } finally {
                    buffer.release();
                }
            }
        }
        for (int offset : new int[]{-128, -2, 2, 127}) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buffer.writeByte(SongIntentMessage.Action.NOTE.ordinal());
                buffer.writeByte(SongNote.D1.ordinal());
                buffer.writeByte(offset);
                boolean rejected = false;
                try { SongIntentMessage.decode(buffer); }
                catch (DecoderException expected) { rejected = true; }
                helper.assertTrue(rejected, "Invalid semitone offset accepted: " + offset);
            } finally {
                buffer.release();
            }
        }
        helper.succeed();
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void allMelodiesRecognizeAcrossTicks(GameTestHelper helper) {
        verifyMelodies(helper, false);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void allMelodiesRecognizeWhenPacketsArriveTogether(GameTestHelper helper) {
        verifyMelodies(helper, true);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void batchedMelodyCompletesItsEffectWithoutRecognitionTimeout(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Song_Effect]"));
        var position = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.OCARINA_OF_TIME.get()));
        player.setHealth(player.getMaxHealth() - 5.0F);
        ZSSPlayerData data = new ZSSPlayerData();
        data.learnSong(ZSSContentIds.HEALING);
        var song = SongCatalog.get(ZSSContentIds.HEALING).orElseThrow();
        SongService.begin(player, data, false);
        song.notes().forEach(note -> SongService.note(player, data, note, 0));
        helper.runAtTickTime(SongService.RECOGNITION_TICKS + 1L, () -> {
            SongService.tick(player, data);
            helper.assertTrue(song.id().equals(data.songState().matchedSong())
                            && player.getHealth() < player.getMaxHealth(),
                    "Matched melody timed out or executed before playback completed");
        });
        helper.runAtTickTime(song.minimumDuration() + 1L, () -> {
            SongService.tick(player, data);
            helper.assertTrue(player.getHealth() == player.getMaxHealth()
                            && data.healingCooldownUntil() > player.level().getGameTime() && !data.songState().open(),
                    "Batched melody did not complete its healing effect and cooldown");
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft", timeoutTicks = 100)
    public static void unlearnedTimeoutRetryAndCancelRemainEnforced(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Song_Retry]"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ZSSRegistries.OCARINA_OF_TIME.get()));
        ZSSPlayerData data = new ZSSPlayerData();
        List<SongNote> notes = SongCatalog.get(ZSSContentIds.HEALING).orElseThrow().notes();
        SongService.begin(player, data, false);
        notes.forEach(note -> SongService.note(player, data, note, 0));
        helper.assertTrue(data.songState().matchedSong() == null, "Unlearned melody was accepted");
        helper.runAtTickTime(SongService.RECOGNITION_TICKS + 1L, () -> {
            SongService.tick(player, data);
            helper.assertTrue(data.songState().open() && data.songState().notes().isEmpty()
                            && data.songState().recognitionDeadline() == 0L,
                    "Recognition timeout did not clear the candidate for retry");
            data.learnSong(ZSSContentIds.HEALING);
            notes.forEach(note -> SongService.note(player, data, note, 0));
            helper.assertTrue(ZSSContentIds.HEALING.equals(data.songState().matchedSong()),
                    "Learned melody failed on retry after timeout");
            SongService.note(player, data, SongNote.D1, 0);
            helper.assertTrue(data.songState().notes().equals(notes), "Input continued during song playback");
            SongService.cancel(player, data);
            notes.forEach(note -> SongService.note(player, data, note, 0));
            helper.assertTrue(!data.songState().open() && data.songState().notes().isEmpty(),
                    "Cancelled session accepted more input");
            helper.succeed();
        });
    }

    @GameTest(template = "zssgametests.dungeon_empty", templateNamespace = "minecraft", timeoutTicks = 150)
    public static void templeWarpSongsAreLearnedAndRecognized(GameTestHelper helper) {
        Map<WarpStoneBlock.WarpSong, String> scores = Map.of(
                WarpStoneBlock.WarpSong.BOLERO, "vAvA>v>v", WarpStoneBlock.WarpSong.MINUET, "A^<><>",
                WarpStoneBlock.WarpSong.PRELUDE, "^>^><^", WarpStoneBlock.WarpSong.OATH, ">vAv>^",
                WarpStoneBlock.WarpSong.NOCTURNE, "<>>A<>v", WarpStoneBlock.WarpSong.REQUIEM, "AvA>vA",
                WarpStoneBlock.WarpSong.SERENADE, "Av>><");
        for (var warpSong : WarpStoneBlock.WarpSong.values()) {
            WarpStoneBlock stone = switch (warpSong) {
                case BOLERO -> ZSSRegistries.WARP_STONE_BOLERO.get();
                case MINUET -> ZSSRegistries.WARP_STONE_MINUET.get();
                case PRELUDE -> ZSSRegistries.WARP_STONE_PRELUDE.get();
                case OATH -> ZSSRegistries.WARP_STONE_OATH.get();
                case NOCTURNE -> ZSSRegistries.WARP_STONE_NOCTURNE.get();
                case REQUIEM -> ZSSRegistries.WARP_STONE_REQUIEM.get();
                case SERENADE -> ZSSRegistries.WARP_STONE_SERENADE.get();
            };
            BlockPos destination = helper.absolutePos(new BlockPos(2 + 2 * (warpSong.ordinal() % 4), 1,
                    2 + 2 * (warpSong.ordinal() / 4)));
            helper.getLevel().setBlock(destination, stone.defaultBlockState(), 3);
            for (var instrument : List.of(ZSSRegistries.FAIRY_OCARINA.get(), ZSSRegistries.OCARINA_OF_TIME.get(),
                    ZSSRegistries.GODDESS_HARP.get())) {
                ServerPlayer player = testPlayer(helper);
                Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 8)));
                player.setPos(start);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(instrument));
                ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(IllegalStateException::new);
                stone.use(stone.defaultBlockState(), helper.getLevel(), destination, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(destination), Direction.UP, destination, false));
                helper.assertTrue(data.songs().contains(warpSong.songId())
                                && data.warpPoints().get(warpSong.songId()).pos().equals(destination),
                        "Warp stone did not teach and bind song: " + warpSong);
                SongService.begin(player, data, false);
                scores.get(warpSong).chars().mapToObj(symbol -> switch (symbol) {
                    case 'A' -> SongNote.D1;
                    case 'v' -> SongNote.F1;
                    case '>' -> SongNote.A2;
                    case '<' -> SongNote.B2;
                    case '^' -> SongNote.D2;
                    default -> throw new AssertionError("Invalid score symbol");
                }).forEach(note -> SongService.note(player, data, note, 0));
                helper.assertTrue(warpSong.songId().equals(data.songState().matchedSong()),
                        "Temple score was not recognized: " + warpSong + "/" + instrument);
                helper.runAtTickTime(130L, () -> {
                    try {
                        SongService.tick(player, data);
                        Vec3 expected = instrument == ZSSRegistries.FAIRY_OCARINA.get()
                                ? start : Vec3.atBottomCenterOf(destination.above());
                        helper.assertTrue(!data.songState().open() && player.position().distanceToSqr(expected) < 0.001D,
                                "Wrong warp result: " + warpSong + "/" + instrument);
                    } finally {
                        player.getAdvancements().stopListening();
                        player.discard();
                    }
                });
            }
        }
        helper.runAtTickTime(131L, helper::succeed);
    }

    @GameTest(template = "zssgametests.empty", templateNamespace = "minecraft")
    public static void forgetSuggestionsOnlyContainPlayersLearnedSongs(GameTestHelper helper) {
        ServerPlayer player = testPlayer(helper);
        ServerPlayer other = testPlayer(helper);
        try {
            ZSSPlayerData data = ZSSCapabilities.get(player).orElseThrow(IllegalStateException::new);
            data.learnSong(ZSSContentIds.BOLERO);
            data.learnSong(ZSSContentIds.SARIA);
            ZSSCapabilities.get(other).orElseThrow(IllegalStateException::new).learnSong(ZSSContentIds.EPONA);
            var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
            var source = player.createCommandSourceStack().withPermission(2);
            String command = "zss song forget ";
            var suggestions = dispatcher.getCompletionSuggestions(dispatcher.parse(command, source)).join();
            helper.assertTrue(suggestions.getList().stream().map(s -> s.getText()).collect(java.util.stream.Collectors.toSet())
                            .equals(java.util.Set.of(ZSSContentIds.BOLERO.toString(), ZSSContentIds.SARIA.toString())),
                    "Forget suggestions contain unlearned songs or another player's songs");
            data.forgetSong(ZSSContentIds.BOLERO);
            var afterForget = dispatcher.getCompletionSuggestions(dispatcher.parse(command, source)).join();
            helper.assertTrue(afterForget.getList().size() == 1
                            && afterForget.getList().get(0).getText().equals(ZSSContentIds.SARIA.toString()),
                    "Forgotten song remained in suggestions");
            data.forgetSong(ZSSContentIds.SARIA);
            helper.assertTrue(dispatcher.getCompletionSuggestions(dispatcher.parse(command, source)).join().isEmpty(),
                    "Player with no learned songs received forget suggestions");
        } finally {
            player.getAdvancements().stopListening();
            other.getAdvancements().stopListening();
            player.discard();
            other.discard();
        }
        helper.succeed();
    }

    private static ServerPlayer testPlayer(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Song]"));
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player) {
            @Override public void send(Packet<?> packet) {}
            @Override public void send(Packet<?> packet, PacketSendListener listener) {}
        };
        return player;
    }

    private static void verifyMelodies(GameTestHelper helper, boolean batched) {
        Map<ResourceLocation, List<SongNote>> melodies = new LinkedHashMap<>();
        SongCatalog.all().forEach((id, song) -> melodies.put(id, song.notes()));
        List<SongNote> custom = List.of(SongNote.D1, SongNote.D2, SongNote.D1, SongNote.D2,
                SongNote.F1, SongNote.A2, SongNote.B2, SongNote.F1);
        melodies.put(ZSSContentIds.SCARECROW, custom);
        List<String> failures = new ArrayList<>();
        for (var instrument : List.of(ZSSRegistries.FAIRY_OCARINA.get(), ZSSRegistries.OCARINA_OF_TIME.get(),
                ZSSRegistries.GODDESS_HARP.get())) {
            for (boolean learnAll : new boolean[]{false, true}) {
                melodies.forEach((song, notes) -> {
                    FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[ZSS_Melody]"));
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(instrument));
                    ZSSPlayerData data = new ZSSPlayerData();
                    data.setScarecrowMelody(custom, 0L);
                    if (learnAll) ZSSContentIds.SONGS.forEach(data::learnSong);
                    else data.learnSong(song);
                    SongService.begin(player, data, false);
                    if (batched) {
                        helper.runAtTickTime(1L, () -> notes.forEach(note -> SongService.note(player, data, note, 0)));
                    } else {
                        for (int index = 0; index < notes.size(); index++) {
                            SongNote note = notes.get(index);
                            helper.runAtTickTime(index + 1L, () -> SongService.note(player, data, note, 0));
                        }
                    }
                    helper.runAtTickTime(10L, () -> {
                        SongService.tick(player, data);
                        if (!song.equals(data.songState().matchedSong())
                                || !notes.equals(data.songState().notes())
                                || data.songState().recognitionDeadline() != 0L) {
                            failures.add(song.getPath() + "/" + instrument + "/all=" + learnAll);
                        }
                        SongService.cancel(player, data);
                        helper.assertTrue(!data.songState().open(), "Cancel left melody active");
                    });
                });
            }
        }
        helper.runAtTickTime(11L, () -> {
            helper.assertTrue(failures.isEmpty(), "Learned melodies lost input: " + failures);
            helper.succeed();
        });
    }
}
