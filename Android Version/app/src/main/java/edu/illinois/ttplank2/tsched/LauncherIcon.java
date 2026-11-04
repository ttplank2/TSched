package edu.illinois.ttplank2.tsched;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Keeps the app's home-screen icon in the color of the accent. Android cannot repaint an icon, so the
 * manifest has one version of it (an activity alias) for each accent, all opening the same screen, and
 * exactly one is switched on at a time. {@link #apply} switches on the one for the saved accent.
 */
final class LauncherIcon {
    /** The wheel's colors in the wheel's order, as used in the alias and picture names. */
    private static final String[] WHEEL_NAMES = {
        "red", "orange", "yellow", "lime", "green", "teal", "blue", "purple", "magenta", "pink",
    };

    /** The name of every icon version: "Icon" and the accent's name, such as IconYellow or IconFruity. */
    static final List<String> ALIASES;

    static {
        List<String> names = new ArrayList<>();
        for (String wheel : WHEEL_NAMES) {
            names.add(aliasNamed(wheel));
        }
        for (String secret : AccentColors.SECRET_KEYS) {
            names.add(aliasNamed(secret));
        }
        ALIASES = Collections.unmodifiableList(names);
    }

    private LauncherIcon() {
    }

    private static String aliasNamed(String accentName) {
        return "Icon" + Character.toUpperCase(accentName.charAt(0)) + accentName.substring(1);
    }

    /** The icon version for a saved accent (a color's hex, or a secret choice); yellow's if it is not recognized. */
    static String aliasFor(String accentKey) {
        int secret = AccentColors.secretIndex(accentKey);
        if (secret >= 0) {
            return aliasNamed(AccentColors.SECRET_KEYS[secret]);
        }
        int color = AccentSetting.parse(accentKey);
        for (int i = 0; i < EventColors.COLORS.length; i++) {
            if (EventColors.COLORS[i] == color) {
                return aliasNamed(WHEEL_NAMES[i]);
            }
        }
        return aliasNamed(WHEEL_NAMES[2]);
    }

    /** The name of an icon version's picture files, such as ic_launcher_fruity. */
    static String iconNameFor(String alias) {
        return "ic_launcher_" + alias.substring("Icon".length()).toLowerCase(Locale.ROOT);
    }

    /** Switches on the icon version for the saved accent, if it is not already on, and switches off the rest. */
    static void apply(Context context) {
        PackageManager packages = context.getPackageManager();
        String wanted = aliasFor(new AccentSetting(context).loadKey());
        String fallbackOn = aliasFor(AccentSetting.format(EventColors.DEFAULT));
        if (!isOn(packages, context, wanted, fallbackOn)) {
            // The new one goes on first, so there is never a moment with no icon.
            set(packages, context, wanted, PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        }
        for (String alias : ALIASES) {
            if (!alias.equals(wanted) && isOn(packages, context, alias, fallbackOn)) {
                set(packages, context, alias, PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
            }
        }
    }

    /** Whether the saved accent's icon is the one that is on now. */
    static boolean isCurrent(Context context) {
        String wanted = aliasFor(new AccentSetting(context).loadKey());
        return isOn(context.getPackageManager(), context, wanted, aliasFor(AccentSetting.format(EventColors.DEFAULT)));
    }

    /** A version the manifest never changed is on only if it is the default one. */
    private static boolean isOn(PackageManager packages, Context context, String alias, String defaultAlias) {
        int state = packages.getComponentEnabledSetting(componentOf(context, alias));
        return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && alias.equals(defaultAlias));
    }

    private static void set(PackageManager packages, Context context, String alias, int state) {
        packages.setComponentEnabledSetting(componentOf(context, alias), state, PackageManager.DONT_KILL_APP);
    }

    private static ComponentName componentOf(Context context, String alias) {
        return new ComponentName(context.getPackageName(), "edu.illinois.ttplank2.tsched." + alias);
    }
}
