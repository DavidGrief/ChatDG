package ru.davidgrief.chatdg.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import ru.davidgrief.chatdg.ChatDG;
import ru.davidgrief.chatdg.listeners.LegacyChatListener;

import java.lang.reflect.Method;

public final class CompatibilityManager {
    private final ChatDG plugin;
    private final VersionManager versionManager;
    private volatile boolean modernPaperChat;

    public CompatibilityManager(ChatDG plugin, VersionManager versionManager) {
        this.plugin = plugin;
        this.versionManager = versionManager;
    }

    public void registerChatCompatibility() {
        modernPaperChat = tryRegisterPaperAsyncChat();
        if (!modernPaperChat) {
            Bukkit.getPluginManager().registerEvents(new LegacyChatListener(plugin), plugin);
            plugin.getLogger().info("Chat compatibility: AsyncPlayerChatEvent (legacy/common Bukkit layer).");
        } else {
            plugin.getLogger().info("Chat compatibility: Paper AsyncChatEvent adapter.");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean tryRegisterPaperAsyncChat() {
        try {
            final Class<?> raw = Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            if (!Event.class.isAssignableFrom(raw)) return false;
            final Listener marker = new Listener() { };
            Bukkit.getPluginManager().registerEvent((Class<? extends Event>) raw, marker, EventPriority.HIGHEST, new EventExecutor() {
                public void execute(Listener listener, Event event) {
                    try {
                        Method cancelled = event.getClass().getMethod("setCancelled", boolean.class);
                        cancelled.invoke(event, true);
                        Player player = (Player) event.getClass().getMethod("getPlayer").invoke(event);
                        String message = extractAdventurePlainText(event);
                        Bukkit.getScheduler().runTask(plugin, new Runnable() {
                            public void run() { plugin.getChatService().handle(player, message); }
                        });
                    } catch (Throwable ex) {
                        plugin.getLogger().warning("Ошибка Paper chat adapter: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                    }
                }
            }, plugin, true);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String extractAdventurePlainText(Event event) throws Exception {
        Object component = event.getClass().getMethod("message").invoke(event);
        try {
            Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            Object instance = serializer.getMethod("plainText").invoke(null);
            Method serialize = null;
            for (Method method : instance.getClass().getMethods()) {
                if (method.getName().equals("serialize") && method.getParameterTypes().length == 1) { serialize = method; break; }
            }
            if (serialize != null) return String.valueOf(serialize.invoke(instance, component));
        } catch (Throwable ignored) { }
        return String.valueOf(component);
    }

    public VersionManager getVersionManager() { return versionManager; }
    public boolean isModernPaperChat() { return modernPaperChat; }
}
