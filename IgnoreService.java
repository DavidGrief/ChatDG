package ru.davidgrief.chatdg.config;

import org.bukkit.configuration.file.YamlConfiguration;
import ru.davidgrief.chatdg.ChatDG;

import java.io.File;
import java.io.IOException;

public final class ConfigManager {
    private final ChatDG plugin;
    private File messagesFile;
    private File wordsFile;
    private File dataFile;
    private YamlConfiguration messages;
    private YamlConfiguration words;
    private YamlConfiguration data;

    public ConfigManager(ChatDG plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Не удалось создать папку плагина: " + plugin.getDataFolder());
        }
        plugin.saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        saveResourceIfMissing("words.yml");
        saveResourceIfMissing("data.yml");
        reloadAll();
    }

    public void reloadAll() {
        plugin.reloadConfig();
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        wordsFile = new File(plugin.getDataFolder(), "words.yml");
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        messages = YamlConfiguration.loadConfiguration(messagesFile);
        words = YamlConfiguration.loadConfiguration(wordsFile);
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveResourceIfMissing(String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
    }

    public synchronized void saveDataSync() throws IOException {
        data.save(dataFile);
    }

    public YamlConfiguration getMessages() { return messages; }
    public YamlConfiguration getWords() { return words; }
    public YamlConfiguration getData() { return data; }
    public File getDataFile() { return dataFile; }
}
