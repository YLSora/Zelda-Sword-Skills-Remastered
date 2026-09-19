package zeldaswordskills_remastered.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ZSSConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
        builder = new ForgeConfigSpec.Builder();
        CLIENT = new Client(builder);
        CLIENT_SPEC = builder.build();
    }

    private ZSSConfig() {
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue hideMagicBar;
        public final ForgeConfigSpec.BooleanValue naviMovingLight;
        public final ForgeConfigSpec.BooleanValue naviSilent;
        public final ForgeConfigSpec.BooleanValue replacePickupSounds;

        private Client(ForgeConfigSpec.Builder builder) {
            builder.push("hud");
            hideMagicBar = builder.comment("Hide the magic bar except while holding the sneak key (Shift by default).")
                    .define("hideMagicBar", false);
            builder.pop();
            builder.push("player");
            naviMovingLight = builder.comment("Show Navi's moving light locally, without changing server lighting.")
                    .define("naviMovingLight", true);
            naviSilent = builder.comment("Silence Navi's voice and interaction sounds on this client.")
                    .define("naviSilent", false);
            replacePickupSounds = builder.comment("Replace item pickup sounds with the ZeldaSwordSkills_Remastered pickup sounds on this client.")
                    .define("replacePickupSounds", true);
            builder.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue startingMagic;
        public final ForgeConfigSpec.DoubleValue maximumMagic;
        public final ForgeConfigSpec.BooleanValue resetSkillsOnDeath;
        public final ForgeConfigSpec.BooleanValue giveStartingItems;
        public final ForgeConfigSpec.BooleanValue naturalMonsterSpawning;
        public final ForgeConfigSpec.BooleanValue canTargetPlayers;
        public final ForgeConfigSpec.IntValue intentsPerSecond;
        public final ForgeConfigSpec.IntValue maximumHeartContainers;
        public final ForgeConfigSpec.BooleanValue flashAssaultCanRemovePlayerArmor;
        public final ForgeConfigSpec.BooleanValue generateStructures;
        public final ForgeConfigSpec.BooleanValue masterMode;
        public final ForgeConfigSpec.BooleanValue generateZssLoot;
        public final ForgeConfigSpec.IntValue weatherSongCooldownTicks;
        public final ForgeConfigSpec.IntValue timeSongCooldownTicks;

        private Server(ForgeConfigSpec.Builder builder) {
            builder.push("player");
            startingMagic = builder.comment("Magic available to a new player.")
                    .defineInRange("startingMagic", 50.0D, 0.0D, 1000.0D);
            maximumMagic = builder.comment("Maximum magic capacity a player can obtain.")
                    .defineInRange("maximumMagic", 200.0D, 50.0D, 1000.0D);
            resetSkillsOnDeath = builder.comment("Reset learned sword skills on death, independently of keepInventory. Heart Containers and other progression are retained.")
                    .define("resetSkillsOnDeath", false);
            giveStartingItems = builder.comment("Give one Basic Sword Skill Orb and one Link's House on a player's first initialization. Disabled grants are not retried later.")
                    .define("giveStartingItems", true);
            canTargetPlayers = builder.comment("Allow lock-on targeting of other players.")
                    .define("canTargetPlayers", false);
            maximumHeartContainers = builder.comment("Maximum number of Heart Containers a player can obtain.")
                    .defineInRange("maximumHeartContainers", 20, 5, 100);
            flashAssaultCanRemovePlayerArmor = builder.comment("Allow Flash Assault to remove another player's chest armor.")
                    .define("flashAssaultCanRemovePlayerArmor", false);
            builder.pop();

            builder.push("worldgen");
            generateStructures = builder.comment("Generate Zelda temples and secret rooms in newly generated chunks.")
                    .define("generateStructures", true);
            builder.pop();

            builder.push("gameplay");
            naturalMonsterSpawning = builder.comment("Allow natural ZSS monster spawning. Also requires doMobSpawning; does not affect dungeon encounters or explicit summons.")
                    .define("naturalMonsterSpawning", true);
            masterMode = builder.comment("Enable Master Mode: five base hearts, double enemy health and damage, no dropped Small Hearts, and no natural magic regeneration.")
                    .define("masterMode", false);
            generateZssLoot = builder.comment("Generate ZeldaSwordSkills_Remastered loot in injected vanilla loot tables.")
                    .define("generateZssLoot", true);
            builder.pop();

            builder.push("songs");
            weatherSongCooldownTicks = builder.comment("Cooldown between non-creative Song of Storms world changes.")
                    .defineInRange("weatherCooldownTicks", 24000, 0, 2400000);
            timeSongCooldownTicks = builder.comment("Cooldown between non-creative Sun's Song world changes.")
                    .defineInRange("timeCooldownTicks", 24000, 0, 2400000);
            builder.pop();

            builder.push("network");
            intentsPerSecond = builder.comment("Maximum accepted gameplay intent packets per player per second.")
                    .defineInRange("intentsPerSecond", 10, 1, 20);
            builder.pop();
        }
    }
}
