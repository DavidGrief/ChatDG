package ru.davidgrief.chatdg.commands;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import ru.davidgrief.chatdg.ChatDG;
import ru.davidgrief.chatdg.compatibility.CommandConflictManager;
import ru.davidgrief.chatdg.models.MuteRecord;
import ru.davidgrief.chatdg.models.MuteType;
import ru.davidgrief.chatdg.utils.DurationParser;
import ru.davidgrief.chatdg.utils.TextUtil;
import ru.davidgrief.chatdg.utils.TimeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CommandManager implements CommandExecutor, TabCompleter {
    private final ChatDG plugin;
    private boolean muteCommandsEnabled = true;
    private String externalMutePlugin;

    public CommandManager(ChatDG plugin){this.plugin=plugin;}

    public void register(){
        externalMutePlugin = new CommandConflictManager(plugin).findExternalMutePlugin();
        muteCommandsEnabled = externalMutePlugin == null;

        for(String name:Arrays.asList("chatdg","msg","r","clearchat","chat","mute","unmute","muteinfo","ignore","unignore","ignorelist")){
            PluginCommand c=plugin.getCommand(name);
            if(c==null){plugin.getLogger().severe("Команда отсутствует в plugin.yml: "+name);continue;}
            c.setExecutor(this);
            c.setTabCompleter(this);
        }

        if(!muteCommandsEnabled){
            plugin.getLogger().warning("Обнаружены команды мута другого плагина: " + externalMutePlugin + ". Команды /mute, /unmute и /muteinfo ChatDG отключены.");
        }
    }

    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        String n=command.getName().toLowerCase();
        if(isMuteCommand(n) && !muteCommandsEnabled){
            if(dispatchExternalMuteCommand(sender,n,args))return true;
            plugin.getMessageService().send(sender,"mute.external-command-conflict",Collections.singletonMap("plugin",externalMutePlugin==null?"другой плагин":externalMutePlugin));
            return true;
        }
        if(n.equals("chatdg"))return chatdg(sender,args);
        if(n.equals("msg"))return msg(sender,args);
        if(n.equals("r"))return reply(sender,args);
        if(n.equals("clearchat"))return clearChat(sender);
        if(n.equals("chat"))return chatToggle(sender,args);
        if(n.equals("mute"))return mute(sender,args);
        if(n.equals("unmute"))return unmute(sender,args);
        if(n.equals("muteinfo"))return muteInfo(sender,args);
        if(n.equals("ignore"))return ignore(sender,args,true);
        if(n.equals("unignore"))return ignore(sender,args,false);
        if(n.equals("ignorelist"))return ignoreList(sender);
        return true;
    }

    private boolean chatdg(CommandSender s,String[] a){
        String sub=a.length==0?"help":a[0].toLowerCase();
        if(sub.equals("reload")){if(!perm(s,"chatdg.reload"))return true;plugin.reloadPlugin();plugin.getMessageService().send(s,"admin.reload-success");return true;}
        if(sub.equals("info")){if(!perm(s,"chatdg.admin"))return true;Map<String,String>r=new HashMap<String,String>();r.put("version",plugin.getDescription().getVersion());r.put("author","Давид Grief");r.put("minecraft",plugin.getVersionManager().getMinecraftVersion());r.put("java",System.getProperty("java.version"));r.put("platform",plugin.getVersionManager().getPlatform());r.put("luckperms",yes(plugin.getLuckPermsHook().isAvailable()));r.put("placeholderapi",yes(plugin.getPlaceholderAPIHook().isAvailable()));r.put("adventure",yes(plugin.getAdventureHook().isAdventureAvailable()));r.put("mutes",String.valueOf(plugin.getMuteService().activeCount()));for(String line:plugin.getConfigManager().getMessages().getStringList("admin.info")){String x=line;for(Map.Entry<String,String>e:r.entrySet())x=x.replace("%"+e.getKey()+"%",e.getValue());s.sendMessage(plugin.getColorUtil().colorize(x));}return true;}
        if(sub.equals("version")){Map<String,String>r=Collections.singletonMap("version",plugin.getDescription().getVersion());plugin.getMessageService().send(s,"admin.version",r);return true;}
        plugin.getMessageService().sendList(s,"admin.help");return true;
    }
    private String yes(boolean b){return b?"&aON":"&cOFF";}

    private boolean msg(CommandSender s,String[] a){if(!(s instanceof Player)){plugin.getMessageService().send(s,"errors.players-only");return true;}if(!perm(s,"chatdg.msg"))return true;if(a.length<2){plugin.getMessageService().send(s,"usage.msg");return true;}Player t=Bukkit.getPlayer(a[0]);if(t==null){plugin.getMessageService().send(s,"msg.player-offline");return true;}plugin.getPrivateMessageService().send((Player)s,t,TextUtil.join(a,1));return true;}
    private boolean reply(CommandSender s,String[] a){if(!(s instanceof Player)){plugin.getMessageService().send(s,"errors.players-only");return true;}if(!perm(s,"chatdg.msg"))return true;if(a.length<1){plugin.getMessageService().send(s,"usage.reply");return true;}plugin.getPrivateMessageService().reply((Player)s,TextUtil.join(a,0));return true;}

    private boolean clearChat(CommandSender s){
        if(!perm(s,"chatdg.clearchat"))return true;

        String actor=s.getName();
        Player executor=s instanceof Player?(Player)s:null;
        int lines=plugin.getConfig().getInt("clearchat.lines",200);
        lines=Math.max(100,Math.min(lines,500));
        boolean preserveBypass=plugin.getConfig().getBoolean("clearchat.preserve-bypass",true);
        boolean clearExecutor=plugin.getConfig().getBoolean("clearchat.clear-executor-even-with-bypass",true);
        String blank="\u00A7r";
        Map<String,String>r=Collections.singletonMap("player",actor);

        for(Player p:Bukkit.getOnlinePlayers()){
            boolean isExecutor=executor!=null&&p.getUniqueId().equals(executor.getUniqueId());
            boolean hasBypass=p.hasPermission("chatdg.clearchat.bypass");
            boolean shouldClear=!preserveBypass||!hasBypass||(isExecutor&&clearExecutor);

            if(shouldClear){
                for(int i=0;i<lines;i++)p.sendMessage(blank);
            }
            plugin.getMessageService().send(p,"clearchat.done",r);
        }

        if(!(s instanceof Player))plugin.getMessageService().send(s,"clearchat.done",r);
        return true;
    }

    private boolean chatToggle(CommandSender s,String[] a){if(a.length==0||a[0].equalsIgnoreCase("status")){Map<String,String>r=Collections.singletonMap("status",plugin.getChatService().isGlobalEnabled()?"&aON":"&cOFF");plugin.getMessageService().send(s,"chat.status",r);return true;}if(!perm(s,"chatdg.chat.toggle"))return true;if(a[0].equalsIgnoreCase("on")){plugin.getChatService().setGlobalEnabled(true);plugin.getMessageService().send(s,"chat.enabled");return true;}if(a[0].equalsIgnoreCase("off")){plugin.getChatService().setGlobalEnabled(false);plugin.getMessageService().send(s,"chat.disabled");return true;}plugin.getMessageService().send(s,"usage.chat");return true;}

    private boolean mute(CommandSender s,String[] a){if(!perm(s,"chatdg.mute"))return true;if(a.length<1){plugin.getMessageService().send(s,"usage.mute");return true;}OfflinePlayer target=Bukkit.getOfflinePlayer(a[0]);long duration=0L;int reasonStart=1;if(a.length>1){long parsed=DurationParser.parseMillis(a[1]);if(parsed>=0L){duration=parsed;reasonStart=2;}}String reason=reasonStart<a.length?TextUtil.join(a,reasonStart):plugin.getConfigManager().getMessages().getString("mute.default-reason","Не указана");long now=System.currentTimeMillis();UUID admin=s instanceof Player?((Player)s).getUniqueId():new UUID(0L,0L);MuteType type=duration==0L?MuteType.PERMANENT:MuteType.TIMED;MuteRecord record=new MuteRecord(target.getUniqueId(),target.getName()==null?a[0]:target.getName(),admin,s.getName(),reason,now,duration==0L?0L:now+duration,type);plugin.getMuteService().mute(record);Map<String,String>r=new HashMap<String,String>();r.put("admin",s.getName());r.put("player",record.getPlayerName());r.put("reason",reason);r.put("time",duration==0L?"навсегда":DurationParser.format(duration));plugin.getMessageService().broadcast("mute.created",r);Player online=target.getPlayer();if(online!=null)plugin.getMessageService().send(online,"mute.received",r);return true;}
    private boolean unmute(CommandSender s,String[] a){if(!perm(s,"chatdg.unmute"))return true;if(a.length<1){plugin.getMessageService().send(s,"usage.unmute");return true;}OfflinePlayer t=Bukkit.getOfflinePlayer(a[0]);Map<String,String>r=Collections.singletonMap("player",t.getName()==null?a[0]:t.getName());plugin.getMessageService().send(s,plugin.getMuteService().unmute(t.getUniqueId())?"mute.removed":"mute.not-muted",r);return true;}
    private boolean muteInfo(CommandSender s,String[] a){if(!perm(s,"chatdg.muteinfo"))return true;if(a.length<1){plugin.getMessageService().send(s,"usage.muteinfo");return true;}OfflinePlayer t=Bukkit.getOfflinePlayer(a[0]);MuteRecord m=plugin.getMuteService().getActiveMute(t.getUniqueId());if(m==null){plugin.getMessageService().send(s,"mute.not-muted",Collections.singletonMap("player",a[0]));return true;}Map<String,String>r=new HashMap<String,String>();r.put("player",m.getPlayerName());r.put("admin",m.getAdminName());r.put("reason",m.getReason());r.put("issued",TimeUtil.formatDate(m.getIssuedAt()));r.put("expires",m.getType()==MuteType.PERMANENT?"permanent":TimeUtil.formatDate(m.getExpiresAt()));r.put("type",m.getType().name());for(String line:plugin.getConfigManager().getMessages().getStringList("mute.info")){String x=line;for(Map.Entry<String,String>e:r.entrySet())x=x.replace("%"+e.getKey()+"%",e.getValue());s.sendMessage(plugin.getColorUtil().colorize(x));}return true;}

    private boolean ignore(CommandSender s,String[] a,boolean add){if(!(s instanceof Player)){plugin.getMessageService().send(s,"errors.players-only");return true;}if(!perm(s,"chatdg.ignore"))return true;if(a.length<1){plugin.getMessageService().send(s,add?"usage.ignore":"usage.unignore");return true;}Player p=(Player)s;OfflinePlayer t=Bukkit.getOfflinePlayer(a[0]);if(t.getUniqueId().equals(p.getUniqueId())){plugin.getMessageService().send(s,"ignore.self");return true;}boolean changed=plugin.getIgnoreService().toggleIgnore(p.getUniqueId(),t.getUniqueId(),add);Map<String,String>r=Collections.singletonMap("player",t.getName()==null?a[0]:t.getName());plugin.getMessageService().send(s,add?(changed?"ignore.added":"ignore.already"):(changed?"ignore.removed":"ignore.not-found"),r);return true;}
    private boolean ignoreList(CommandSender s){if(!(s instanceof Player)){plugin.getMessageService().send(s,"errors.players-only");return true;}if(!perm(s,"chatdg.ignore"))return true;Set<UUID> set=plugin.getIgnoreService().getIgnored(((Player)s).getUniqueId());if(set.isEmpty()){plugin.getMessageService().send(s,"ignore.empty");return true;}List<String>names=new ArrayList<String>();for(UUID u:set){OfflinePlayer o=Bukkit.getOfflinePlayer(u);names.add(o.getName()==null?u.toString():o.getName());}plugin.getMessageService().send(s,"ignore.list",Collections.singletonMap("players",TextUtil.join(names,", ")));return true;}

    private boolean perm(CommandSender s,String p){if(s.hasPermission(p)||s.hasPermission("chatdg.admin"))return true;plugin.getMessageService().send(s,"errors.no-permission");return false;}

    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        String n=command.getName().toLowerCase();
        if(isMuteCommand(n) && !muteCommandsEnabled)return Collections.emptyList();
        List<String> values=new ArrayList<String>();
        if(n.equals("chatdg")&&args.length==1)values.addAll(Arrays.asList("help","info","version","reload"));else if(n.equals("chat")&&args.length==1)values.addAll(Arrays.asList("on","off","status"));else if((n.equals("msg")||n.equals("mute")||n.equals("unmute")||n.equals("muteinfo")||n.equals("ignore")||n.equals("unignore"))&&args.length==1)for(Player p:Bukkit.getOnlinePlayers())values.add(p.getName());else if(n.equals("mute")&&args.length==2)values.addAll(Arrays.asList("10s","5m","2h","3d","7d","1d12h","permanent"));
        if(args.length==0)return values;String prefix=args[args.length-1].toLowerCase();List<String>out=new ArrayList<String>();for(String v:values)if(v.toLowerCase().startsWith(prefix))out.add(v);return out;
    }

    private boolean isMuteCommand(String name){return name.equals("mute")||name.equals("unmute")||name.equals("muteinfo");}
    private boolean dispatchExternalMuteCommand(CommandSender sender,String name,String[] args){
        if(externalMutePlugin==null||externalMutePlugin.trim().isEmpty())return false;
        String namespace=externalMutePlugin.toLowerCase(Locale.ROOT).replace(" ","");
        StringBuilder line=new StringBuilder(namespace).append(':').append(name);
        if(args.length>0)line.append(' ').append(TextUtil.join(Arrays.asList(args)," "));
        try{return Bukkit.dispatchCommand(sender,line.toString());}catch(Throwable ex){
            if(plugin.isDebug())plugin.getLogger().warning("Не удалось передать /"+name+" плагину "+externalMutePlugin+": "+ex.getMessage());
            return false;
        }
    }
    public boolean areMuteCommandsEnabled(){return muteCommandsEnabled;}
    public String getExternalMutePlugin(){return externalMutePlugin;}
}
