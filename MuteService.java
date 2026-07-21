package ru.davidgrief.chatdg.hooks;

import org.bukkit.entity.Player;
import ru.davidgrief.chatdg.ChatDG;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LuckPermsHook {
    private static final long CACHE_TTL = 5000L;
    private final ChatDG plugin;
    private final Map<UUID, CachedMeta> cache = new ConcurrentHashMap<UUID, CachedMeta>();
    private volatile boolean available;
    private Object luckPerms;
    private Method getUserManager;

    public LuckPermsHook(ChatDG plugin) {
        this.plugin = plugin;
    }

    public void load() {
        cache.clear();
        available = false;
        luckPerms = null;
        org.bukkit.plugin.Plugin dependency = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        if (dependency == null) {
            plugin.getLogger().info("LuckPerms не найден. Prefix/suffix/group будут пустыми.");
            return;
        }
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider", false, dependency.getClass().getClassLoader());
            Method get = provider.getMethod("get");
            luckPerms = get.invoke(null);
            getUserManager = luckPerms.getClass().getMethod("getUserManager");
            available = true;
            plugin.getLogger().info("LuckPerms успешно подключён.");
        } catch (Throwable ex) {
            plugin.getLogger().warning("LuckPerms найден, но hook не удалось активировать: " + ex.getClass().getSimpleName());
            if (plugin.isDebug()) ex.printStackTrace();
        }
    }

    public String getPrefix(Player player) { return getMeta(player).prefix; }
    public String getSuffix(Player player) { return getMeta(player).suffix; }
    public String getGroup(Player player) { return getMeta(player).group; }

    public String getMetaValue(Player player, String key) {
        if (!available || player == null || key == null) return "";
        try {
            Object user = getUser(player.getUniqueId());
            if (user == null) return "";
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object result = metaData.getClass().getMethod("getMetaValue", String.class).invoke(metaData, key);
            return result == null ? "" : String.valueOf(result);
        } catch (Throwable ex) {
            return "";
        }
    }

    private CachedMeta getMeta(Player player) {
        if (!available || player == null) return CachedMeta.EMPTY;
        long now = System.currentTimeMillis();
        CachedMeta cached = cache.get(player.getUniqueId());
        if (cached != null && cached.validUntil > now) return cached;
        try {
            Object user = getUser(player.getUniqueId());
            if (user == null) return CachedMeta.EMPTY;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            String prefix = stringOrEmpty(metaData.getClass().getMethod("getPrefix").invoke(metaData));
            String suffix = stringOrEmpty(metaData.getClass().getMethod("getSuffix").invoke(metaData));
            String group = stringOrEmpty(user.getClass().getMethod("getPrimaryGroup").invoke(user));
            CachedMeta fresh = new CachedMeta(prefix, suffix, group, now + CACHE_TTL);
            cache.put(player.getUniqueId(), fresh);
            return fresh;
        } catch (Throwable ex) {
            if (plugin.isDebug()) plugin.getLogger().warning("Ошибка чтения LuckPerms meta: " + ex.getMessage());
            return CachedMeta.EMPTY;
        }
    }

    private Object getUser(UUID uuid) throws Exception {
        Object manager = getUserManager.invoke(luckPerms);
        return manager.getClass().getMethod("getUser", UUID.class).invoke(manager, uuid);
    }

    private String stringOrEmpty(Object value) { return value == null ? "" : String.valueOf(value); }

    public void invalidate(UUID uuid) { if (uuid != null) cache.remove(uuid); }
    public void clearCache() { cache.clear(); }
    public boolean isAvailable() { return available; }

    private static final class CachedMeta {
        private static final CachedMeta EMPTY = new CachedMeta("", "", "", 0L);
        private final String prefix;
        private final String suffix;
        private final String group;
        private final long validUntil;
        private CachedMeta(String prefix, String suffix, String group, long validUntil) {
            this.prefix = prefix; this.suffix = suffix; this.group = group; this.validUntil = validUntil;
        }
    }
}
