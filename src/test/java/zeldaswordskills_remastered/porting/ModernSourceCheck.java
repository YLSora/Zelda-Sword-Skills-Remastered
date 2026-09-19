package zeldaswordskills_remastered.porting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Repository cleanup audit; runs on Java 17 without loading Minecraft or Forge. */
public final class ModernSourceCheck {
    private static final Pattern OLD_JAVA = Pattern.compile(
            "\\b(?:cpw\\.mods|net\\.minecraftforge\\.fml\\.common\\.network|"
            + "net\\.minecraft\\.(?:entity|item|block)\\.|zeldaswordskills_remastered\\.api\\.|"
            + "mods\\.battlegear2|hunternif\\.voxelmap|hunternif\\.antiqueatlas)|"
            + "\\b(?:SidedProxy|IExtendedEntityProperties|NBTTagCompound|NBTTagList|"
            + "SimpleNetworkWrapper|DataWatcher|getStateFromMeta|getMetaFromState|getMetadata)\\b");
    private static final Pattern OLD_BUILD = Pattern.compile(
            "(?i)jcenter\\s*\\(|http://(?:files\\.minecraftforge|maven)|"
            + "ForgeGradle:[12]\\.|net\\.minecraftforge\\.gradle\\.forge|"
            + "eclipse[/\\\\]libs|legacy[/\\\\]1\\.8\\.9|"
            + "gradle-[234]\\.[^/]*-(?:bin|all)\\.zip|battlegear|antiqueatlas");
    private static final List<String> RETIRED_PATHS = List.of(
            "legacy", ".reference/DynamicSwordSkills", "eclipse/libs",
            "src/main/java/zeldaswordskills_remastered/api",
            "src/main/java/zeldaswordskills_remastered/ClientProxy.java",
            "src/main/java/zeldaswordskills_remastered/CommonProxy.java",
            "src/main/java/zeldaswordskills_remastered/ServerProxy.java",
            "src/main/resources/.gitignore", "src/main/resources/versionlist.json");

    private ModernSourceCheck() {}

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Path report = root.resolve("build/reports/porting/stage17/modern-sources.txt");
        List<String> errors = new ArrayList<>();
        for (String name : RETIRED_PATHS) {
            if (Files.exists(root.resolve(name))) errors.add("Retired path: " + name);
        }
        List<Path> sources = files(root.resolve("src"));
        int javaCount = 0;
        for (Path file : sources) {
            String name = relative(root, file);
            String leaf = file.getFileName().toString();
            if (leaf.equals("mcmod.info") || leaf.endsWith(".lang") || leaf.equals("battlegear_at.cfg")) {
                errors.add("Retired resource: " + name);
            }
            if (!leaf.endsWith(".java") || file.equals(root.resolve(
                    "src/test/java/zeldaswordskills_remastered/porting/ModernSourceCheck.java"))) continue;
            javaCount++;
            checkText(root, file, OLD_JAVA, errors);
        }
        List<Path> buildFiles = new ArrayList<>(List.of(root.resolve("build.gradle"),
                root.resolve("settings.gradle"), root.resolve("gradle.properties")));
        buildFiles.addAll(files(root.resolve("gradle")).stream()
                .filter(file -> file.toString().endsWith(".gradle") || file.toString().endsWith(".properties")).toList());
        for (Path file : buildFiles) checkText(root, file, OLD_BUILD, errors);
        Pattern numericVariant = Pattern.compile("\\b(?:getInt|putInt)\\s*\\(\\s*\"(?:mode|kind)\"");
        for (String name : List.of("ToolProjectile", "ThrownBomb")) {
            checkText(root, root.resolve("src/main/java/zeldaswordskills_remastered/entity/projectile/" + name + ".java"),
                    numericVariant, errors);
        }
        require(root, "gradle/wrapper/gradle-wrapper.properties", "gradle-8.1.1-bin.zip", errors);
        require(root, "gradle.properties", "minecraft_version=1.20.1", errors);
        require(root, "build.gradle", "JavaLanguageVersion.of(17)", errors);
        require(root, "src/main/resources/META-INF/mods.toml", "modLoader=\"javafml\"", errors);
        errors.sort(String::compareTo);
        String summary = "Java sources scanned: " + javaCount + "\nBuild files scanned: " + buildFiles.size()
                + "\nRetired paths checked: " + RETIRED_PATHS.size() + "\nErrors: " + errors.size() + "\n";
        Files.createDirectories(report.getParent());
        Files.writeString(report, summary + String.join("\n", errors) + "\n", StandardCharsets.UTF_8);
        System.out.print(summary);
        if (!errors.isEmpty()) throw new IllegalStateException(String.join("\n", errors));
    }

    private static List<Path> files(Path directory) throws IOException {
        try (var stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private static void checkText(Path root, Path file, Pattern forbidden, List<String> errors) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int index = 0; index < lines.size(); index++) {
            var match = forbidden.matcher(lines.get(index));
            if (match.find()) errors.add(relative(root, file) + ":" + (index + 1) + ": " + match.group());
        }
    }

    private static void require(Path root, String name, String text, List<String> errors) throws IOException {
        if (!Files.readString(root.resolve(name), StandardCharsets.UTF_8).contains(text)) {
            errors.add(name + ": missing modern baseline " + text);
        }
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
