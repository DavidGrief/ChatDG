package ru.davidgrief.chatdg.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.davidgrief.chatdg.ChatDG;
import ru.davidgrief.chatdg.models.MuteRecord;
import ru.davidgrief.chatdg.utils.DurationParser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PrivateMessageService {
    private final ChatDG plugin; private final Map<UUID,UUID> lastPartner=new ConcurrentHashMap<UUID,UUID>();
    public PrivateMessageService(ChatDG plugin){this.plugin=plugin;}
    public void send(Player sender,Player target,String message){
        if(sender.equals(target)){plugin.getMessageService().send(sender,"msg.self");return;}
        MuteRecord mute=plugin.getMuteService().getActiveMute(sender.getUniqueId());if(mute!=null){Map<String,String>m=new HashMap<String,String>();m.put("reason",mute.getReason());m.put("time",mute.getType().name().equals("PERMANENT")?"permanent":DurationParser.format(mute.getExpiresAt()-System.currentTimeMillis()));plugin.getMessageService().send(sender,"mute.chat-blocked",m);return;}
        if(plugin.getIgnoreService().isIgnoring(target.getUniqueId(),sender.getUniqueId())){plugin.getMessageService().send(sender,"msg.ignored");return;}
        String clean=plugin.getColorUtil().sanitizeUserColors(message,sender.hasPermission("chatdg.msg.color"),sender.hasPermission("chatdg.msg.rgb"));
        Map<String,String> r=new HashMap<String,String>();r.put("sender",sender.getName());r.put("receiver",target.getName());r.put("message",clean);
        String out=plugin.getMessageService().format("msg.outgoing",sender,r);String in=plugin.getMessageService().format("msg.incoming",sender,r);sender.sendMessage(out);target.sendMessage(in);lastPartner.put(sender.getUniqueId(),target.getUniqueId());lastPartner.put(target.getUniqueId(),sender.getUniqueId());
    }
    public void reply(Player sender,String message){UUID uuid=lastPartner.get(sender.getUniqueId());if(uuid==null){plugin.getMessageService().send(sender,"msg.no-reply-target");return;}Player target=Bukkit.getPlayer(uuid);if(target==null){plugin.getMessageService().send(sender,"msg.player-offline");return;}send(sender,target,message);}
    public void clear(UUID uuid){lastPartner.remove(uuid);}
}
