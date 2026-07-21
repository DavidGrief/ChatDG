package ru.davidgrief.chatdg.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.davidgrief.chatdg.ChatDG;

public final class PlayerConnectionListener implements Listener {
    private final ChatDG plugin;
    public PlayerConnectionListener(ChatDG plugin){this.plugin=plugin;}
    @EventHandler public void onQuit(PlayerQuitEvent event){
        plugin.getAntiSpamService().clear(event.getPlayer().getUniqueId());
        plugin.getPrivateMessageService().clear(event.getPlayer().getUniqueId());
        plugin.getLuckPermsHook().invalidate(event.getPlayer().getUniqueId());
    }
}
