package ru.davidgrief.chatdg.compatibility;

import org.bukkit.Bukkit;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionManager {
    private static final Pattern MC_VERSION = Pattern.compile("(?:MC: )?([0-9]+)(?:\\.([0-9]+))?(?:\\.([0-9]+))?");
    private final String bukkitVersion;
    private final String serverVersion;
    private final int major;
    private final int minor;
    private final int patch;

    public VersionManager() {
        this.bukkitVersion = safe(Bukkit.getBukkitVersion());
        this.serverVersion = safe(Bukkit.getVersion());
        int[] parsed = parseVersion(bukkitVersion);
        this.major = parsed[0];
        this.minor = parsed[1];
        this.patch = parsed[2];
    }

    private int[] parseVersion(String input) {
        Matcher matcher = MC_VERSION.matcher(input);
        if (matcher.find()) {
            return new int[]{parse(matcher.group(1)), parse(matcher.group(2)), parse(matcher.group(3))};
        }
        matcher = MC_VERSION.matcher(serverVersion);
        if (matcher.find()) {
            return new int[]{parse(matcher.group(1)), parse(matcher.group(2)), parse(matcher.group(3))};
        }
        return new int[]{1, 12, 2};
    }

    private int parse(String value) {
        if (value == null) return 0;
        try { return Integer.parseInt(value); } catch (NumberFormatException ex) { return 0; }
    }

    private String safe(String value) { return value == null ? "unknown" : value; }

    public boolean supportsHexColors() {
        return major >= 26 || (major == 1 && minor >= 16);
    }

    public boolean isAtLeast(int wantedMajor, int wantedMinor) {
        if (major != wantedMajor) return major > wantedMajor;
        return minor >= wantedMinor;
    }

    public String getMinecraftVersion() {
        if (major >= 26) return major + (minor > 0 ? "." + minor : "") + (patch > 0 ? "." + patch : "");
        return major + "." + minor + (patch > 0 ? "." + patch : "");
    }

    public String getBukkitVersion() { return bukkitVersion; }
    public String getServerVersion() { return serverVersion; }

    public String getPlatform() {
        String value = serverVersion.toLowerCase(Locale.ROOT);
        if (value.contains("purpur")) return "Purpur";
        if (value.contains("paper")) return "Paper";
        if (value.contains("spigot")) return "Spigot";
        if (value.contains("bukkit") || value.contains("craftbukkit")) return "Bukkit";
        return "Unknown/Compatible Bukkit fork";
    }
}
