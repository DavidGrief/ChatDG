package ru.davidgrief.chatdg.listeners;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import ru.davidgrief.chatdg.ChatDG;

public final class LegacyChatListener implements Listener {
    private final ChatDG plugin;
    public LegacyChatListener(ChatDG plugin){this.plugin=plugin;}

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void onChat(final AsyncPlayerChatEvent event){
        event.setCancelled(true);
        final String message=event.getMessage();
        Bukkit.getScheduler().runTask(plugin,new Runnable(){public void run(){plugin.getChatService().handle(event.getPlayer(),message);}});
    }
}
