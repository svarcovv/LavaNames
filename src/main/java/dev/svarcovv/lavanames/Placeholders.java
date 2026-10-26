package dev.svarcovv.lavanames;

import org.bukkit.Statistic;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;

public final class Placeholders {
    private final LavaNames plugin;
    private Plugin papiPlugin;
    private Method papiMethod;
    private Plugin luckPlugin;
    private LuckHook luckHook;
    private long nextWarning;

    private record LuckHook(Class<?> api, Method manager, Method user, Method cached, Method meta,
                            Method prefix, Method suffix) {
        static LuckHook create(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> api = Class.forName("net.luckperms.api.LuckPerms", true, loader);
            Class<?> data = Class.forName("net.luckperms.api.cacheddata.CachedMetaData", true, loader);
            return new LuckHook(api, api.getMethod("getUserManager"),
                    Class.forName("net.luckperms.api.model.user.UserManager", true, loader).getMethod("getUser", UUID.class),
                    Class.forName("net.luckperms.api.model.PermissionHolder", true, loader).getMethod("getCachedData"),
                    Class.forName("net.luckperms.api.cacheddata.CachedDataManager", true, loader).getMethod("getMetaData"),
                    data.getMethod("getPrefix"), data.getMethod("getSuffix"));
        }
    }

    public Placeholders(LavaNames plugin) {
        this.plugin = plugin;
    }

    public String resolve(Player player, String template) {
        String text = template;
        if (text.contains("%luckperms_prefix%")) {
            text = text.replace("%luckperms_prefix%", meta(player, true));
        }
        if (text.contains("%luckperms_suffix%")) {
            text = text.replace("%luckperms_suffix%", meta(player, false));
        }
        text = text.replace("%player_name%", player.getName())
                .replace("%player_health_rounded%", Long.toString(Math.round(player.getHealth())))
                .replace("%player_health%", decimal(player.getHealth()))
                .replace("%player_max_health%", decimal(player.getAttribute(Attribute.MAX_HEALTH).getValue()))
                .replace("%statistic_player_kills%", Integer.toString(player.getStatistic(Statistic.PLAYER_KILLS)))
                .replace("%statistic_deaths%", Integer.toString(player.getStatistic(Statistic.DEATHS)));
        Plugin papi = plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI");
        if (text.indexOf('%') >= 0 && papi != null && papi.isEnabled()) {
            try {
                if (papiPlugin != papi || papiMethod == null) {
                    Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, papi.getClass().getClassLoader());
                    papiMethod = api.getMethod("setPlaceholders", Player.class, String.class);
                    papiPlugin = papi;
                }
                text = (String) papiMethod.invoke(null, player, text);
            } catch (ReflectiveOperationException | RuntimeException error) {
                warn(error);
            }
        }
        return text;
    }

    private String meta(Player player, boolean prefix) {
        Plugin luckPerms = plugin.getServer().getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) {
            return "";
        }
        try {
            if (luckPlugin != luckPerms || luckHook == null) {
                luckHook = LuckHook.create(luckPerms.getClass().getClassLoader());
                luckPlugin = luckPerms;
            }
            Object api = plugin.getServer().getServicesManager().load(luckHook.api());
            if (api == null) {
                return "";
            }
            Object manager = luckHook.manager().invoke(api);
            Object user = luckHook.user().invoke(manager, player.getUniqueId());
            if (user == null) {
                return "";
            }
            Object cached = luckHook.cached().invoke(user);
            Object data = luckHook.meta().invoke(cached);
            String value = (String) (prefix ? luckHook.prefix() : luckHook.suffix()).invoke(data);
            return value == null ? "" : value;
        } catch (ReflectiveOperationException | RuntimeException error) {
            warn(error);
            return "";
        }
    }

    private void warn(Exception error) {
        long now = System.currentTimeMillis();
        if (now >= nextWarning) {
            nextWarning = now + 60_000;
            plugin.log("placeholder-failed", java.util.Map.of("error", error.toString()));
        }
    }

    static String decimal(double value) {
        return BigDecimal.valueOf(Math.round(value * 10) / 10.0).stripTrailingZeros().toPlainString();
    }
}
