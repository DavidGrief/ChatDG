package ru.davidgrief.chatdg.hooks;

import org.bukkit.entity.Player;
import ru.davidgrief.chatdg.ChatDG;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AdventureHook {
    private static final Pattern AMP_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern ANGLE_HEX = Pattern.compile("<##([A-Fa-f0-9]{6})>");
    private static final Pattern PLAIN_HEX = Pattern.compile("(?<![:&§#])#([A-Fa-f0-9]{6})");
    private final ChatDG plugin;
    private volatile boolean adventureAvailable;
    private volatile boolean miniMessageAvailable;

    public AdventureHook(ChatDG plugin) { this.plugin = plugin; }

    public void load() {
        adventureAvailable = classExists("net.kyori.adventure.text.Component");
        miniMessageAvailable = classExists("net.kyori.adventure.text.minimessage.MiniMessage");
        if (plugin.isDebug()) plugin.getLogger().info("Adventure: " + adventureAvailable + ", MiniMessage: " + miniMessageAvailable);
    }

    public boolean trySend(Player player, String rawText, String legacyColored, String configuredMode) {
        if (!adventureAvailable || player == null) return false;
        String mode = configuredMode == null ? "AUTO" : configuredMode.toUpperCase(Locale.ROOT);
        try {
            Object component;
            if ("MINIMESSAGE".equals(mode)) {
                if (!miniMessageAvailable) return false;
                component = deserializeMiniMessage(toMiniMessage(rawText));
            } else if ("ADVENTURE".equals(mode)) {
                component = deserializeLegacySection(legacyColored);
            } else if ("AUTO".equals(mode) && miniMessageAvailable && containsMiniMessage(rawText)) {
                component = deserializeMiniMessage(toMiniMessage(rawText));
            } else {
                return false;
            }
            if (component == null) return false;
            return invokeAdventureSend(player, component);
        } catch (Throwable ex) {
            if (plugin.isDebug()) plugin.getLogger().warning("Adventure fallback: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            return false;
        }
    }

    private Object deserializeMiniMessage(String text) throws Exception {
        Class<?> mini = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage");
        Object instance = mini.getMethod("miniMessage").invoke(null);
        return mini.getMethod("deserialize", String.class).invoke(instance, text);
    }

    private Object deserializeLegacySection(String text) throws Exception {
        Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        Object instance = serializer.getMethod("legacySection").invoke(null);
        return findSingleArgMethod(instance.getClass(), "deserialize").invoke(instance, text);
    }

    private boolean invokeAdventureSend(Player player, Object component) throws Exception {
        for (Method method : player.getClass().getMethods()) {
            if (!method.getName().equals("sendMessage") || method.getParameterTypes().length != 1) continue;
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter.isInstance(component) || parameter.getName().equals("net.kyori.adventure.text.Component")) {
                method.invoke(player, component);
                return true;
            }
        }
        return false;
    }

    private Method findSingleArgMethod(Class<?> type, String name) throws NoSuchMethodException {
        for (Method method : type.getMethods()) if (method.getName().equals(name) && method.getParameterTypes().length == 1) return method;
        throw new NoSuchMethodException(name);
    }

    private boolean containsMiniMessage(String text) {
        if (text == null) return false;
        String value = text.toLowerCase(Locale.ROOT);
        return value.contains("<gradient:") || value.contains("<rainbow") || value.matches(".*</?[a-z_]+(?:[: ][^>]*)?>.*");
    }

    private String toMiniMessage(String text) {
        if (text == null) return "";
        String value = replaceHex(text, AMP_HEX);
        value = replaceHex(value, ANGLE_HEX);
        value = replaceHex(value, PLAIN_HEX);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '&' && i + 1 < value.length()) {
                String tag = legacyTag(Character.toLowerCase(value.charAt(i + 1)));
                if (tag != null) { out.append(tag); i++; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }

    private String replaceHex(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(out, Matcher.quoteReplacement("<#" + matcher.group(1) + ">"));
        matcher.appendTail(out);
        return out.toString();
    }

    private String legacyTag(char code) {
        switch (code) {
            case '0': return "<black>"; case '1': return "<dark_blue>"; case '2': return "<dark_green>"; case '3': return "<dark_aqua>";
            case '4': return "<dark_red>"; case '5': return "<dark_purple>"; case '6': return "<gold>"; case '7': return "<gray>";
            case '8': return "<dark_gray>"; case '9': return "<blue>"; case 'a': return "<green>"; case 'b': return "<aqua>";
            case 'c': return "<red>"; case 'd': return "<light_purple>"; case 'e': return "<yellow>"; case 'f': return "<white>";
            case 'k': return "<obfuscated>"; case 'l': return "<bold>"; case 'm': return "<strikethrough>"; case 'n': return "<underlined>";
            case 'o': return "<italic>"; case 'r': return "<reset>"; default: return null;
        }
    }

    private boolean classExists(String name) {
        try { Class.forName(name, false, plugin.getClass().getClassLoader()); return true; }
        catch (Throwable ignored) { try { Class.forName(name); return true; } catch (Throwable ignoredAgain) { return false; } }
    }

    public boolean isAdventureAvailable() { return adventureAvailable; }
    public boolean isMiniMessageAvailable() { return miniMessageAvailable; }
}
