package dev.svarcovv.lavanames;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public record Settings(List<Line> lines, int updateInterval, int visibilityInterval,
                       double viewDistance, boolean hideSneaking, boolean hideInvisible,
                       boolean hideVanilla, boolean shadow, boolean seeThrough, int lineWidth) {
    public record Line(String text, float height, float scale) {}

    public static Settings read(ConfigurationSection config) {
        List<String> texts = config.getStringList("display-text");
        if (texts.isEmpty() || texts.size() > 16 || texts.stream().anyMatch(t -> t.length() > 4096)) {
            throw new IllegalArgumentException("display-text: требуется от 1 до 16 строк длиной до 4096 символов");
        }
        float height = (float) number(config, "display-y-offset", 0.25, -4, 16);
        float scale = (float) number(config, "display-scale", 1, 0.1, 4);
        List<Line> lines = new ArrayList<>();
        if (bool(config, "separate-line-displays", false)) {
            for (int i = 0; i < texts.size(); i++) {
                String path = "line-settings." + i;
                float lineHeight = (float) number(config, path + ".height",
                        height + (texts.size() - 1 - i) * 0.25 * scale, -4, 16);
                float lineScale = (float) number(config, path + ".scale", scale, 0.1, 4);
                lines.add(new Line(texts.get(i), lineHeight, lineScale));
            }
        } else {
            lines.add(new Line(String.join("<br><reset>", texts), height, scale));
        }
        return new Settings(List.copyOf(lines), integer(config, "update-interval-ticks", 10, 1, 1200),
                integer(config, "visibility-interval-ticks", 2, 1, 100),
                number(config, "view-distance", 48, 4, 128), bool(config, "hide-when-sneaking", true),
                bool(config, "hide-when-invisible", true), bool(config, "hide-vanilla-names", true),
                bool(config, "shadow", true), bool(config, "see-through", false),
                integer(config, "line-width", 300, 1, 4096));
    }

    private static boolean bool(ConfigurationSection config, String key, boolean fallback) {
        Object raw = config.get(key);
        if (raw != null && !(raw instanceof Boolean)) {
            throw new IllegalArgumentException(key + ": требуется true или false");
        }
        return config.getBoolean(key, fallback);
    }

    private static int integer(ConfigurationSection config, String key, int fallback, int min, int max) {
        double value = number(config, key, fallback, min, max);
        if (value != Math.rint(value)) {
            throw new IllegalArgumentException(key + ": требуется целое число");
        }
        return (int) value;
    }

    private static double number(ConfigurationSection config, String key, double fallback, double min, double max) {
        Object raw = config.get(key);
        if (raw != null && !(raw instanceof Number)) {
            throw new IllegalArgumentException(key + ": требуется число");
        }
        double value = config.getDouble(key, fallback);
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(key + ": допустимо от " + min + " до " + max);
        }
        return value;
    }
}
