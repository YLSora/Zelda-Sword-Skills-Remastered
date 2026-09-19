package zeldaswordskills_remastered.data;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.advancements.FrameType;
import net.minecraft.advancements.critereon.ImpossibleTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.advancements.AdvancementProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import zeldaswordskills_remastered.ZeldaSwordSkills_Remastered;
import zeldaswordskills_remastered.registry.ZSSRegistries;
import zeldaswordskills_remastered.registry.ZSSContentIds;
import zeldaswordskills_remastered.song.SongCatalog;
import zeldaswordskills_remastered.item.BigKeyItem;
import zeldaswordskills_remastered.worldgen.DungeonType;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Modern data-driven replacement for the legacy Zelda achievement page. */
public final class ZSSAdvancementProvider extends AdvancementProvider {
    private static final List<ResourceLocation> SONGS = ZSSContentIds.SONGS.stream().sorted().toList();
    private static final List<Node> NODES = Stream.concat(List.of(
            n("adventure_begins", null, "kokiri_sword"),
            n("bombs_away", "adventure_begins", "standard_bomb"), n("bomb_junkie", "bombs_away", "bomb_bag", true),
            n("boss_battle", "adventure_begins", "big_key"),
            n("temple.water", "boss_battle", "big_key"),
            n("temple.desert", "boss_battle", "big_key"),
            n("temple.ice", "boss_battle", "big_key"),
            n("temple.forest", "temple.desert", "big_key"),
            n("temple.earth", "temple.forest", "big_key"),
            n("temple.fire", "temple.earth", "big_key"),
            n("temple.end", "temple.fire", "big_key", true),
            n("sword.pendant", "adventure_begins", "pendant_power", false), n("sword.master", "sword.pendant", "master_sword", false),
            n("sword.tempered", "sword.master", "master_ore"), n("sword.evil", "sword.tempered", "tempered_sword"),
            n("sword.golden", "sword.evil", "golden_sword", false), n("sword.flame", "sword.golden", "sacred_flame_din"),
            n("sword.true", "sword.flame", "true_master_sword", true), n("shield.mirror", "sword.true", "mirror_shield", true),
            n("fairy.catcher", "adventure_begins", "fairy_bottle"), n("fairy.emerald", "fairy.catcher", "emerald"),
            n("fairy.bow", "fairy.emerald", "hero_bow"), n("fairy.bow_max", "fairy.bow", "light_arrow", true),
            n("fairy.enchantment", "fairy.emerald", "melon_seeds"), n("fairy.slingshot", "fairy.enchantment", "slingshot", false),
            n("fairy.supershot", "fairy.slingshot", "supershot", true), n("hammer.wood", "adventure_begins", "wooden_hammer"),
            n("hammer.silver", "hammer.wood", "silver_gauntlets"), n("hammer.skull", "hammer.silver", "peg_rusty"),
            n("hammer.golden", "hammer.skull", "megaton_hammer", true), n("mask.trader", "adventure_begins", "zeldas_letter"),
            n("mask.sold", "mask.trader", "keaton_mask"), n("mask.shop", "mask.sold", "mask_of_truth", true),
            n("skill.basic", null, "skill_orb", false), n("combo.basic", "skill.basic", "wooden_sword", false),
            n("combo.perfect", "combo.basic", "iron_sword", false), n("combo.legend", "combo.perfect", "diamond_sword", true),
            n("skill.gain", "skill.basic", "skill_orb", false), n("skill.all_types", "skill.gain", "skill_orb", true),
            n("skill.heart", "skill.basic", "small_heart", false), n("skill.heartbar", "skill.heart", "skill_orb", false),
            n("skill.hearts_galore", "skill.heartbar", "skill_orb", true), n("fairy.boomerang", "skill.heartbar", "magic_boomerang", true),
            n("sword.broken", "skill.heartbar", "broken_sword"), n("treasure.first", "sword.broken", "tentacle"),
            n("treasure.second", "treasure.first", "pocket_egg"), n("treasure.biggoron", "treasure.second", "biggoron_sword", true),
            n("orca.thief", null, "whip"), n("orca.deknighted", "orca.thief", "knights_crest"),
            n("orca.request", "orca.deknighted", "writable_book"), n("orca.first", "orca.request", "stick"),
            n("orca.canopener", "orca.first", "iron_chestplate"), n("orca.second", "orca.first", "skill_orb"),
            n("orca.master", "orca.second", "darknut_sword", true), n("ocarina.craft", null, "fairy_ocarina"),
            n("ocarina.song", "ocarina.craft", "writable_book", false), n("ocarina.scarecrow", "ocarina.song", "pumpkin", true),
            n("ocarina.maestro", "ocarina.song", "ocarina_of_time", true)
    ).stream(), SONGS.stream().map(song -> n("ocarina.song." + song.getPath(), "ocarina.song", "fairy_ocarina"))).toList();

    public ZSSAdvancementProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, List.of(ZSSAdvancementProvider::generate));
    }

    public static List<String> ids() {
        return NODES.stream().map(Node::id).toList();
    }

    private static void generate(HolderLookup.Provider registries, Consumer<Advancement> writer) {
        Map<String, Advancement> generated = new HashMap<>();
        for (Node node : NODES) {
            ItemStack icon = resolveStack(node.item(), node.id());
            ResourceLocation id = id(node.id());
            ResourceLocation background = node.parent() == null
                    ? ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png") : null;
            Component title = Component.translatable("advancements.zeldaswordskills_remastered." + node.id() + ".title");
            Component description = Component.translatable("advancements.zeldaswordskills_remastered." + node.id() + ".description");
            if (node.id().startsWith("ocarina.song.")) {
                ResourceLocation song = id(node.id().substring("ocarina.song.".length()));
                title = Component.translatable("song." + song.getNamespace() + "." + song.getPath());
                if (!song.equals(ZSSContentIds.SCARECROW)) {
                    String notes = SongCatalog.get(song).orElseThrow().notes().stream().map(note -> switch (note) {
                        case D1 -> "A";
                        case F1 -> "v";
                        case A2 -> ">";
                        case B2 -> "<";
                        case D2 -> "^";
                    }).collect(Collectors.joining(" ", "[ ", " ]"));
                    description = Component.literal(notes);
                }
            }
            boolean hiddenUntilEarned = node.id().startsWith("ocarina.song.");
            DisplayInfo display = new DisplayInfo(icon, title, description, background,
                    node.challenge() ? FrameType.CHALLENGE : FrameType.TASK, true, true, hiddenUntilEarned);
            float[] position = position(node.id());
            display.setLocation(position[0], position[1]);
            Advancement.Builder builder = Advancement.Builder.advancement()
                    .display(display)
                    .addCriterion("event", new ImpossibleTrigger.TriggerInstance());
            if (node.parent() != null) builder.parent(generated.get(node.parent()));
            Advancement advancement = builder.rewards(AdvancementRewards.EMPTY).save(writer, id.toString());
            generated.put(node.id(), advancement);
        }
    }

    private static ItemLike resolve(String name) {
        return switch (name) {
            case "emerald" -> Items.EMERALD;
            case "melon_seeds" -> Items.MELON_SEEDS;
            case "wooden_sword" -> Items.WOODEN_SWORD;
            case "iron_sword" -> Items.IRON_SWORD;
            case "diamond_sword" -> Items.DIAMOND_SWORD;
            case "writable_book" -> Items.WRITABLE_BOOK;
            case "stick" -> Items.STICK;
            case "iron_chestplate" -> Items.IRON_CHESTPLATE;
            case "pumpkin" -> Items.PUMPKIN;
            default -> ZSSRegistries.getItem(name);
        };
    }

    private static ItemStack resolveStack(String item, String id) {
        ItemLike icon = resolve(item);
        if (item.equals("big_key") && id.startsWith("temple.")) {
            String path = switch (id.substring("temple.".length())) {
                case "water" -> "ocean"; case "desert" -> "desert"; case "ice" -> "taiga";
                case "forest" -> "forest"; case "earth" -> "mountain"; case "fire" -> "hell";
                case "end" -> "end"; default -> "";
            };
            DungeonType type = DungeonType.byId(ResourceLocation.fromNamespaceAndPath(
                    ZeldaSwordSkills_Remastered.MOD_ID, path)).orElse(null);
            if (type != null && icon instanceof Item key) return BigKeyItem.forDungeon(key, type.id());
        }
        return new ItemStack(icon);
    }

    private static float[] position(String id) {
        if (id.startsWith("ocarina.song.")) {
            int index = SONGS.indexOf(id(id.substring("ocarina.song.".length())));
            return new float[]{2 + 2 * (index % 4), -2 - 2 * (index / 4)};
        }
        return switch (id) {
            case "adventure_begins" -> new float[]{0, 1};
            case "bombs_away" -> new float[]{-4, -2}; case "bomb_junkie" -> new float[]{-4, -4};
            case "boss_battle" -> new float[]{0, -4};
            case "temple.water" -> new float[]{-2, -6}; case "temple.desert" -> new float[]{0, -6};
            case "temple.ice" -> new float[]{2, -6}; case "temple.forest" -> new float[]{0, -8};
            case "temple.earth" -> new float[]{0, -10}; case "temple.fire" -> new float[]{0, -12};
            case "temple.end" -> new float[]{0, -14};
            case "sword.pendant" -> new float[]{0, -2}; case "sword.master" -> new float[]{-2, -4};
            case "sword.tempered" -> new float[]{-4, -1}; case "sword.evil" -> new float[]{-6, 1};
            case "sword.golden" -> new float[]{-6, 3}; case "sword.flame" -> new float[]{-6, 5};
            case "sword.true" -> new float[]{-6, 7}; case "shield.mirror" -> new float[]{-6, 9};
            case "fairy.catcher" -> new float[]{4, -2}; case "fairy.emerald" -> new float[]{4, -4};
            case "fairy.bow" -> new float[]{2, -3}; case "fairy.bow_max" -> new float[]{2, -5};
            case "fairy.enchantment" -> new float[]{4, -1}; case "fairy.slingshot" -> new float[]{4, -3};
            case "fairy.supershot" -> new float[]{6, -6}; case "hammer.wood" -> new float[]{-6, -2};
            case "hammer.silver" -> new float[]{-3, 3}; case "hammer.skull" -> new float[]{-3, 5};
            case "hammer.golden" -> new float[]{-6, -8}; case "mask.trader" -> new float[]{6, 2};
            case "mask.sold" -> new float[]{0, 6}; case "mask.shop" -> new float[]{0, 8};
            case "skill.basic" -> new float[]{0, 0}; case "combo.basic" -> new float[]{2, 0};
            case "combo.perfect" -> new float[]{4, 0}; case "combo.legend" -> new float[]{3, 2};
            case "skill.gain" -> new float[]{1, -3}; case "skill.all_types" -> new float[]{1, -5};
            case "skill.heart" -> new float[]{0, 2}; case "skill.heartbar" -> new float[]{0, 4};
            case "skill.hearts_galore" -> new float[]{0, 6}; case "fairy.boomerang" -> new float[]{-2, 4};
            case "sword.broken" -> new float[]{3, 4}; case "treasure.first" -> new float[]{3, 6};
            case "treasure.second" -> new float[]{3, 8}; case "treasure.biggoron" -> new float[]{1, 8};
            case "orca.thief" -> new float[]{0, 1}; case "orca.deknighted" -> new float[]{-2, 2};
            case "orca.request" -> new float[]{-2, 4}; case "orca.first" -> new float[]{-2, 6};
            case "orca.canopener" -> new float[]{0, 6}; case "orca.second" -> new float[]{-2, 8};
            case "orca.master" -> new float[]{-3, 10}; case "ocarina.craft" -> new float[]{0, 0};
            case "ocarina.song" -> new float[]{0, -2}; case "ocarina.scarecrow" -> new float[]{-1, -4};
            case "ocarina.maestro" -> new float[]{0, -6}; default -> new float[]{0, 0};
        };
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ZeldaSwordSkills_Remastered.MOD_ID, path);
    }

    private static Node n(String id, String parent, String item) { return new Node(id, parent, item, false); }
    private static Node n(String id, String parent, String item, boolean challenge) { return new Node(id, parent, item, challenge); }
    private record Node(String id, String parent, String item, boolean challenge) { }
}
