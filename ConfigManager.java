package ru.davidgrief.chatdg.chat;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.davidgrief.chatdg.ChatDG;
import ru.davidgrief.chatdg.models.ChatChannel;
import ru.davidgrief.chatdg.models.MuteRecord;
import ru.davidgrief.chatdg.services.AntiCapsService;
import ru.davidgrief.chatdg.services.AntiSpamService;
import ru.davidgrief.chatdg.services.WordFilterService;
import ru.davidgrief.chatdg.utils.DurationParser;

import java.util.HashMap;
import java.util.Map;

public final class ChatService {
    private final ChatDG plugin;
    private volatile boolean globalEnabled;

    public ChatService(ChatDG plugin){this.plugin=plugin;this.globalEnabled=plugin.getConfig().getBoolean("chat.global.enabled",true);}
    public void reload(){this.globalEnabled=plugin.getConfig().getBoolean("chat.global.enabled",true);}
    public boolean isGlobalEnabled(){return globalEnabled;}
    public void setGlobalEnabled(boolean enabled){this.globalEnabled=enabled;plugin.getConfig().set("chat.global.enabled",enabled);plugin.saveConfig();}

    public void handle(Player sender,String original){
        if(sender==null||original==null||original.trim().isEmpty())return;
        MuteRecord mute=plugin.getMuteService().getActiveMute(sender.getUniqueId());
        if(mute!=null){Map<String,String> r=new HashMap<String,String>();r.put("reason",mute.getReason());r.put("time",mute.getType().name().equals("PERMANENT")?"permanent":DurationParser.format(mute.getExpiresAt()-System.currentTimeMillis()));plugin.getMessageService().send(sender,"mute.chat-blocked",r);return;}
        String symbol=plugin.getConfig().getString("chat.global.symbol","!"); boolean global=symbol!=null&&!symbol.isEmpty()&&original.startsWith(symbol);
        ChatChannel channel=global?ChatChannel.GLOBAL:ChatChannel.LOCAL;
        if(channel==ChatChannel.GLOBAL){
            if(!sender.hasPermission("chatdg.chat.global")){plugin.getMessageService().send(sender,"errors.no-permission");return;}
            if(!globalEnabled&&!sender.hasPermission("chatdg.chat.bypass")){plugin.getMessageService().send(sender,"chat.global-disabled");return;}
        }else{
            if(!sender.hasPermission("chatdg.chat.local")){plugin.getMessageService().send(sender,"errors.no-permission");return;}
            if(!plugin.getConfig().getBoolean("chat.local.enabled",true)){plugin.getMessageService().send(sender,"chat.local-disabled");return;}
        }
        String message=original;
        if(global&&plugin.getConfig().getBoolean("chat.global.remove-symbol",true)) message=message.substring(symbol.length());
        AntiSpamService.Result spam=plugin.getAntiSpamService().check(sender,message); if(spam.blocked){plugin.getMessageService().send(sender,"antispam.blocked");return;}
        AntiCapsService.Result caps=plugin.getAntiCapsService().process(sender,message); if(caps.cancelled){plugin.getMessageService().send(sender,"anticaps.blocked");return;} message=caps.message;
        WordFilterService.Result filter=plugin.getWordFilterService().process(sender,message); if(filter.notify)notifyStaff(sender,message); if(filter.cancelled){plugin.getMessageService().send(sender,"filter.blocked");return;} message=filter.message;
        boolean allowLegacy=sender.hasPermission("chatdg.chat.color");
        boolean allowRgb=sender.hasPermission("chatdg.chat.rgb")||sender.hasPermission("chatdg.chat.hex");
        message=plugin.getColorUtil().sanitizeUserColors(message,allowLegacy,allowRgb);
        message=plugin.getMentionService().process(sender,message);
        String format=plugin.getConfig().getString(channel==ChatChannel.GLOBAL?"chat.global.format":"chat.local.format",channel==ChatChannel.GLOBAL?"&8[&cG&8] %prefix% &f%player% &8» &f%message%":"&8[&aL&8] %prefix% &f%player% &8» &f%message%");
        Map<String,String> extra=new HashMap<String,String>(); extra.put("message",message); String rawRendered=plugin.getPlaceholderService().apply(sender,format,extra);
        Map<String,String> adventureExtra=new HashMap<String,String>(); adventureExtra.put("message",escapeMiniMessageInput(message)); String rawAdventure=plugin.getPlaceholderService().apply(sender,format,adventureExtra);
        String rendered=plugin.getColorUtil().colorize(rawRendered);
        double radius=plugin.getConfig().getDouble("chat.local.radius",100D), radiusSq=radius*radius; boolean ignoreGlobal=plugin.getConfig().getBoolean("ignore.affect-global-chat",true);
        for(Player recipient:Bukkit.getOnlinePlayers()){
            if(channel==ChatChannel.LOCAL){if(!recipient.getWorld().equals(sender.getWorld()))continue;if(radius>=0&&recipient.getLocation().distanceSquared(sender.getLocation())>radiusSq)continue;}
            if((channel==ChatChannel.LOCAL||ignoreGlobal)&&plugin.getIgnoreService().isIgnoring(recipient.getUniqueId(),sender.getUniqueId()))continue;
            plugin.getRichMessageSender().send(recipient,sender,rawAdventure,rendered);
        }
        Bukkit.getConsoleSender().sendMessage(rendered);
    }
    private String escapeMiniMessageInput(String input){return input.replace("\\","\\\\").replace("<","\\<");}
    private void notifyStaff(Player sender,String raw){Map<String,String> r=new HashMap<String,String>();r.put("player",sender.getName());r.put("message",raw);for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("chatdg.staff.notify"))plugin.getMessageService().send(p,"filter.staff-notify",r);}
}
