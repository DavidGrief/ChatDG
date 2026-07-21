package ru.davidgrief.chatdg.hooks;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import ru.davidgrief.chatdg.ChatDG;

import java.lang.reflect.Method;

public final class PlaceholderAPIHook {
    private final ChatDG plugin;
    private volatile boolean available;
    private Method setPlaceholders;

    public PlaceholderAPIHook(ChatDG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        available = false;
        setPlaceholders = null;
        org.bukkit.plugin.Plugin dependency = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (dependency == null) {
            plugin.getLogger().info("PlaceholderAPI не найден. Интеграция отключена.");
            return;
        }
        try {
            Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI", false, dependency.getClass().getClassLoader());
            setPlaceholders = papi.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
            available = true;
            plugin.getLogger().info("PlaceholderAPI успешно подключён.");
        } catch (Throwable ex) {
            plugin.getLogger().warning("PlaceholderAPI найден, но hook не удалось активировать: " + ex.getClass().getSimpleName());
            if (plugin.isDebug()) ex.printStackTrace();
        }
    }

    public String apply(Player player, String text) {
        if (!available || player == null || text == null) return text;
        try {
            Object result = setPlaceholders.invoke(null, player, text);
            return result == null ? text : String.valueOf(result);
        } catch (Throwable ex) {
            if (plugin.isDebug()) plugin.getLogger().warning("Ошибка PlaceholderAPI: " + ex.getMessage());
            return text;
        }
    }

    public boolean isAvailable() { return available; }
}
