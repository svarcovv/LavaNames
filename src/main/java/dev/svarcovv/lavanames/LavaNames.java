package dev.svarcovv.lavanames;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LavaNames extends JavaPlugin {
    private Messages messages;
    private NameManager manager;
    private PacketBridge bridge;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");
        try {
            messages = new Messages(loadYaml("messages.yml"));
            if (!getServer().getMinecraftVersion().equals("1.21.4")) {
                log("unsupported-version", Map.of());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            Settings settings = Settings.read(loadYaml("config.yml"));
            bridge = new PacketBridge();
            manager = new NameManager(this, settings, bridge);
            getServer().getPluginManager().registerEvents(new NameListener(this), this);
            getServer().getWorlds().forEach(world -> manager.cleanupLegacy(world.getEntitiesByClass(org.bukkit.entity.TextDisplay.class)));
            manager.start();
            log("enabled", Map.of());
        } catch (Exception error) {
            if (messages == null) {
                getServer().getConsoleSender().sendMessage(TextFormat.parse("&6☀ | &fНе удалось прочитать настройки LavaNames."));
            } else {
                log("startup-failed", Map.of());
            }
            getLogger().log(java.util.logging.Level.SEVERE, "&6☀ | &fОшибка запуска LavaNames", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.stop();
            manager = null;
        }
        if (messages != null) {
            log("disabled", Map.of());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lavanames.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length != 1) {
            messages.send(sender, "usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "status" -> sender.sendMessage(messages.get("status", manager.status()));
            case "reload" -> {
                try {
                    Settings settings = Settings.read(loadYaml("config.yml"));
                    Messages nextMessages = new Messages(loadYaml("messages.yml"));
                    manager.reconfigure(settings);
                    messages = nextMessages;
                    messages.send(sender, "reloaded");
                } catch (Exception error) {
                    messages.send(sender, "reload-failed");
                    getLogger().log(java.util.logging.Level.WARNING, "&6☀ | &fОшибка настроек LavaNames", error);
                }
            }
            case "refresh" -> {
                getServer().getOnlinePlayers().forEach(player -> manager.reset(player));
                messages.send(sender, "refreshed");
            }
            default -> messages.send(sender, "usage");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("lavanames.admin") || args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("reload", "refresh", "status").stream().filter(value -> value.startsWith(prefix)).toList();
    }

    public NameManager manager() {
        return manager;
    }

    public void log(String key, Map<String, String> replacements) {
        getServer().getConsoleSender().sendMessage(messages.get(key, replacements));
    }

    private YamlConfiguration loadYaml(String name) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(getDataFolder(), name));
        try (InputStreamReader reader = new InputStreamReader(java.util.Objects.requireNonNull(getResource(name)), StandardCharsets.UTF_8)) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        return yaml;
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).isFile()) {
            saveResource(name, false);
        }
    }
}
