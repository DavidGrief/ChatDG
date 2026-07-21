package ru.davidgrief.chatdg.compatibility;

import ru.davidgrief.chatdg.ChatDG;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Detects external mute commands without linking ChatDG to implementation-specific
 * command map classes. Reflection keeps this code safe across old Bukkit and modern Paper.
 */
public final class CommandConflictManager {
    private static final Set<String> MUTE_LABELS = new HashSet<String>(Arrays.asList("mute", "unmute", "muteinfo"));
    private final ChatDG plugin;

    public CommandConflictManager(ChatDG plugin) {
        this.plugin = plugin;
    }

    /**
     * @return external plugin name that owns/declares a mute command, or null when no conflict exists.
     */
    public String findExternalMutePlugin() {
        String declaredOwner = findDeclaredOwner();
        if (declaredOwner != null) return declaredOwner;
        return findRegisteredOwner();
    }

    private String findDeclaredOwner() {
        try {
            Object pluginManager = plugin.getServer().getPluginManager();
            Method getPlugins = pluginManager.getClass().getMethod("getPlugins");
            Object result = getPlugins.invoke(pluginManager);
            if (!(result instanceof Object[])) return null;

            Object[] plugins = (Object[]) result;
            for (Object candidate : plugins) {
                if (candidate == null || candidate == plugin) continue;
                String ownerName = invokeString(candidate, "getName");
                if (ownerName == null || ownerName.equalsIgnoreCase("ChatDG")) continue;

                Object description = invoke(candidate, "getDescription");
                if (description == null) continue;
                Object commandsObject = invoke(description, "getCommands");
                if (!(commandsObject instanceof Map)) continue;

                Map<?, ?> commands = (Map<?, ?>) commandsObject;
                for (Map.Entry<?, ?> entry : commands.entrySet()) {
                    String commandName = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                    if (MUTE_LABELS.contains(commandName)) return ownerName;

                    Object metadata = entry.getValue();
                    if (metadata instanceof Map && aliasesContain(((Map<?, ?>) metadata).get("aliases"))) {
                        return ownerName;
                    }
                }
            }
        } catch (Throwable ex) {
            debug("Не удалось проверить plugin.yml других плагинов: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        return null;
    }

    private boolean aliasesContain(Object aliases) {
        if (aliases == null) return false;
        if (aliases instanceof Collection) {
            for (Object alias : (Collection<?>) aliases) {
                if (MUTE_LABELS.contains(String.valueOf(alias).toLowerCase(Locale.ROOT))) return true;
            }
            return false;
        }
        String raw = String.valueOf(aliases).trim();
        if (raw.startsWith("[") && raw.endsWith("]")) raw = raw.substring(1, raw.length() - 1);
        for (String alias : raw.split(",")) {
            if (MUTE_LABELS.contains(alias.trim().toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String findRegisteredOwner() {
        try {
            Object server = plugin.getServer();
            Method getCommandMap = findMethod(server.getClass(), "getCommandMap");
            if (getCommandMap == null) return null;
            Object commandMap = getCommandMap.invoke(server);
            if (commandMap == null) return null;
            Method getCommand = findMethod(commandMap.getClass(), "getCommand", String.class);
            if (getCommand == null) return null;

            for (String label : MUTE_LABELS) {
                Object command = getCommand.invoke(commandMap, label);
                String owner = ownerOf(command);
                if (owner != null && !owner.equalsIgnoreCase("ChatDG")) return owner;
            }

            Object pluginManager = plugin.getServer().getPluginManager();
            Method getPlugins = pluginManager.getClass().getMethod("getPlugins");
            Object pluginsObject = getPlugins.invoke(pluginManager);
            if (pluginsObject instanceof Object[]) {
                for (Object candidate : (Object[]) pluginsObject) {
                    if (candidate == null || candidate == plugin) continue;
                    String candidateName = invokeString(candidate, "getName");
                    if (candidateName == null) continue;
                    for (String label : MUTE_LABELS) {
                        Object command = getCommand.invoke(commandMap, candidateName.toLowerCase(Locale.ROOT) + ":" + label);
                        if (command != null) return candidateName;
                    }
                }
            }
        } catch (Throwable ex) {
            debug("Не удалось проверить CommandMap: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        return null;
    }

    private String ownerOf(Object command) {
        if (command == null) return null;
        try {
            Object owner = invoke(command, "getPlugin");
            return owner == null ? null : invokeString(owner, "getName");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = findMethod(target.getClass(), methodName);
            return method == null ? null : method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String invokeString(Object target, String methodName) {
        Object result = invoke(target, methodName);
        return result == null ? null : String.valueOf(result);
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                if (!method.isAccessible()) method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        try {
            return type.getMethod(name, parameterTypes);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void debug(String message) {
        if (plugin.isDebug()) plugin.getLogger().info("[Debug] " + message);
    }
}
