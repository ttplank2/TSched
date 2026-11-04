package edu.illinois.ttplank2.tsched;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The app's home-screen icon has one version for every accent, and exactly one is switched on. */
public class LauncherIconTest {
    private static String read(String path) throws IOException {
        File file = new File("src/main/" + path);
        if (!file.exists()) {
            file = new File("app/src/main/" + path);
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static boolean exists(String path) {
        return new File("src/main/" + path).exists() || new File("app/src/main/" + path).exists();
    }

    @Test
    public void everyAccentHasItsOwnIconVersion() {
        Set<String> aliases = new HashSet<>();
        for (int color : EventColors.COLORS) {
            aliases.add(LauncherIcon.aliasFor(AccentSetting.format(color)));
        }
        for (String key : AccentColors.SECRET_KEYS) {
            aliases.add(LauncherIcon.aliasFor(AccentColors.SECRET_PREFIX + key));
        }
        assertEquals(EventColors.COLORS.length + AccentColors.SECRET_KEYS.length, aliases.size());
        assertEquals(aliases, new HashSet<>(LauncherIcon.ALIASES));
    }

    @Test
    public void anUnknownAccentGetsTheDefaultIcon() {
        assertEquals(LauncherIcon.aliasFor(AccentSetting.format(EventColors.DEFAULT)),
                LauncherIcon.aliasFor("not an accent"));
    }

    @Test
    public void theManifestHasAnAliasForEveryVersionAndOnlyTheDefaultIsOn() throws IOException {
        String manifest = read("AndroidManifest.xml");
        Matcher aliases = Pattern.compile("<activity-alias(.*?)</activity-alias>", Pattern.DOTALL).matcher(manifest);
        Set<String> found = new HashSet<>();
        int enabled = 0;
        while (aliases.find()) {
            Matcher name = Pattern.compile("android:name=\"\\.(\\w+)\"").matcher(aliases.group(1));
            assertTrue(name.find());
            found.add(name.group(1));
            if (aliases.group(1).contains("android:enabled=\"true\"")) {
                enabled++;
                assertEquals(LauncherIcon.aliasFor(AccentSetting.format(EventColors.DEFAULT)), name.group(1));
            }
        }
        assertEquals(new HashSet<>(LauncherIcon.ALIASES), found);
        assertEquals(1, enabled);
    }

    @Test
    public void onlyTheAliasesAreLaunchersSoTheAppHasOneIconAtATime() throws IOException {
        String manifest = read("AndroidManifest.xml");
        int launchers = manifest.split("android.intent.category.LAUNCHER", -1).length - 1;
        assertEquals(LauncherIcon.ALIASES.size(), launchers);
    }

    @Test
    public void everyIconVersionHasItsPicturesAndTheLauncherHasOneColorToShow() throws IOException {
        for (String alias : LauncherIcon.ALIASES) {
            String icon = LauncherIcon.iconNameFor(alias);
            assertTrue(icon, exists("res/mipmap-anydpi/" + icon + ".xml"));
            assertTrue(icon, exists("res/drawable/" + icon + "_foreground.xml"));
            assertTrue(icon, read("AndroidManifest.xml").contains("@mipmap/" + icon));
        }
    }
}
