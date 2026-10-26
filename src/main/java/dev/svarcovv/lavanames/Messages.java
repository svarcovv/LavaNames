package dev.svarcovv.lavanames;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;

public final class Messages {
    private final YamlConfiguration values;

    public Messages(YamlConfiguration values) {
        this.values = values;
    }

    public Component get(String key, Map<String, String> replacements) {
        String text = values.getString("prefix", "&6☀ | &f") + values.getString(key, key);
        Component message = TextFormat.parse(text);
        for (Map.Entry<String, String> replacement : replacements.entrySet()) {
            message = message.replaceText(builder -> builder.matchLiteral("{" + replacement.getKey() + "}")
                    .replacement(Component.text(replacement.getValue())));
        }
        return message;
    }

    public void send(CommandSender sender, String key) {
        sender.sendMessage(get(key, Map.of()));
    }
}
