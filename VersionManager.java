package ru.davidgrief.chatdg;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import ru.davidgrief.chatdg.chat.ChatService;
import ru.davidgrief.chatdg.chat.PrivateMessageService;
import ru.davidgrief.chatdg.color.ColorUtil;
import ru.davidgrief.chatdg.commands.CommandManager;
import ru.davidgrief.chatdg.compatibility.CompatibilityManager;
import ru.davidgrief.chatdg.compatibility.VersionManager;
import ru.davidgrief.chatdg.config.ConfigManager;
import ru.davidgrief.chatdg.hooks.AdventureHook;
import ru.davidgrief.chatdg.hooks.LuckPermsHook;
import ru.davidgrief.chatdg.hooks.PlaceholderAPIHook;
import ru.davidgrief.chatdg.listeners.PlayerConnectionListener;
import ru.davidgrief.chatdg.services.AntiCapsService;
import ru.davidgrief.chatdg.services.AntiSpamService;
import ru.davidgrief.chatdg.services.IgnoreService;
import ru.davidgrief.chatdg.services.MentionService;
import ru.davidgrief.chatdg.services.MessageService;
import ru.davidgrief.chatdg.services.MuteService;
import ru.davidgrief.chatdg.services.PlaceholderService;
import ru.davidgrief.chatdg.services.RichMessageSender;
import ru.davidgrief.chatdg.services.WordFilterService;
import ru.davidgrief.chatdg.storage.StorageProvider;
import ru.davidgrief.chatdg.storage.YamlStorage;

public final class ChatDG extends JavaPlugin {
    private ConfigManager configManager;
    private VersionManager versionManager;
    private CompatibilityManager compatibilityManager;
    private ColorUtil colorUtil;
    private LuckPermsHook luckPermsHook;
    private PlaceholderAPIHook placeholderAPIHook;
    private AdventureHook adventureHook;
    private PlaceholderService placeholderService;
    private MessageService messageService;
    private StorageProvider storage;
    private MuteService muteService;
    private IgnoreService ignoreService;
    private AntiSpamService antiSpamService;
    private AntiCapsService antiCapsService;
    private WordFilterService wordFilterService;
    private MentionService mentionService;
    private RichMessageSender richMessageSender;
    private ChatService chatService;
    private PrivateMessageService privateMessageService;
    private CommandManager commandManager;
    private volatile boolean debug;

    @Override
    public void onEnable() {
        try {
            configManager = new ConfigManager(this);
            configManager.loadAll();
            debug = getConfig().getBoolean("debug", false);

            versionManager = new VersionManager();
            colorUtil = new ColorUtil(versionManager);
            configureColors();

            luckPermsHook = new LuckPermsHook(this);
            placeholderAPIHook = new PlaceholderAPIHook(this);
            adventureHook = new AdventureHook(this);
            luckPermsHook.load();
            placeholderAPIHook.load();
            adventureHook.load();

            placeholderService = new PlaceholderService(this);
            messageService = new MessageService(this);
            storage = new YamlStorage(this);
            muteService = new MuteService(storage);
            ignoreService = new IgnoreService(storage);
            antiSpamService = new AntiSpamService(this);
            antiCapsService = new AntiCapsService(this);
            wordFilterService = new WordFilterService(this);
            mentionService = new MentionService(this);
            richMessageSender = new RichMessageSender(this);
            chatService = new ChatService(this);
            privateMessageService = new PrivateMessageService(this);

            commandManager = new CommandManager(this);
            commandManager.register();
            Bukkit.getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
            compatibilityManager = new CompatibilityManager(this, versionManager);
            compatibilityManager.registerChatCompatibility();

            Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
                public void run() { muteService.cleanupExpired(); }
            }, 20L * 60L, 20L * 60L);

            printEnableBanner();
        } catch (Throwable ex) {
            getLogger().severe("Критическая ошибка запуска ChatDG: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            ex.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (storage != null) storage.flush();
        } catch (Throwable ex) {
            getLogger().warning("Ошибка финального сохранения data.yml: " + ex.getMessage());
        }
        getLogger().info("========================================");
        getLogger().info("ChatDG успешно выключен!");
        getLogger().info("========================================");
    }

    public synchronized void reloadPlugin() {
        configManager.reloadAll();
        debug = getConfig().getBoolean("debug", false);
        configureColors();
        luckPermsHook.load();
        placeholderAPIHook.load();
        adventureHook.load();
        luckPermsHook.clearCache();
        antiSpamService.clearAll();
        wordFilterService.reload();
        chatService.reload();
        getLogger().info("Конфигурация ChatDG безопасно перезагружена.");
    }

    private void configureColors() {
        colorUtil.configure(getConfig().getBoolean("colors.legacy", true), getConfig().getBoolean("colors.rgb", true), getConfig().getBoolean("colors.rgb-fallback", true));
    }

    private void printEnableBanner() {
        getLogger().info("  _____ _           _   _____   _____ ");
        getLogger().info(" / ____| |         | | |  __ \\ / ____|");
        getLogger().info("| |    | |__   __ _| |_| |  | | |  __ ");
        getLogger().info("| |    | '_ \\ / _` | __| |  | | | |_ |");
        getLogger().info("| |____| | | | (_| | |_| |__| | |__| |");
        getLogger().info(" \\_____|_| |_|\\__,_|\\__|_____/ \\_____|");
        getLogger().info("========================================");
        getLogger().info("ChatDG успешно запущен!");
        getLogger().info("Версия плагина: " + getDescription().getVersion());
        getLogger().info("Версия сервера: " + versionManager.getMinecraftVersion() + " (" + versionManager.getPlatform() + ")");
        getLogger().info("Java: " + System.getProperty("java.version"));
        getLogger().info("Подпишись на Ютуб канал Давид Grief!");
        getLogger().info("========================================");
    }

    public boolean isDebug() { return debug; }
    public ConfigManager getConfigManager() { return configManager; }
    public VersionManager getVersionManager() { return versionManager; }
    public CompatibilityManager getCompatibilityManager() { return compatibilityManager; }
    public ColorUtil getColorUtil() { return colorUtil; }
    public LuckPermsHook getLuckPermsHook() { return luckPermsHook; }
    public PlaceholderAPIHook getPlaceholderAPIHook() { return placeholderAPIHook; }
    public AdventureHook getAdventureHook() { return adventureHook; }
    public PlaceholderService getPlaceholderService() { return placeholderService; }
    public MessageService getMessageService() { return messageService; }
    public MuteService getMuteService() { return muteService; }
    public IgnoreService getIgnoreService() { return ignoreService; }
    public AntiSpamService getAntiSpamService() { return antiSpamService; }
    public AntiCapsService getAntiCapsService() { return antiCapsService; }
    public WordFilterService getWordFilterService() { return wordFilterService; }
    public MentionService getMentionService() { return mentionService; }
    public RichMessageSender getRichMessageSender() { return richMessageSender; }
    public ChatService getChatService() { return chatService; }
    public PrivateMessageService getPrivateMessageService() { return privateMessageService; }
}
