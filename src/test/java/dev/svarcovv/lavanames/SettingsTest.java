package dev.svarcovv.lavanames;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    private YamlConfiguration config() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("display-text", List.of("%player_name%", "%player_health%"));
        return config;
    }

    @Test
    void combinesLinesIntoOneEntityByDefault() {
        Settings settings = Settings.read(config());
        assertEquals(1, settings.lines().size());
        assertEquals("%player_name%<br><reset>%player_health%", settings.lines().getFirst().text());
    }

    @Test
    void separateLinesKeepIndividualHeightAndScale() {
        YamlConfiguration config = config();
        config.set("separate-line-displays", true);
        config.set("line-settings.0.height", 0.9);
        config.set("line-settings.0.scale", 1.2);
        Settings settings = Settings.read(config);
        assertEquals(2, settings.lines().size());
        assertEquals(0.9f, settings.lines().getFirst().height());
        assertEquals(1.2f, settings.lines().getFirst().scale());
    }

    @Test
    void rejectsIntervalsThatCouldBreakSchedulerOrOverflow() {
        for (Object value : List.of(0, -1, 1201, 1.5, "10", Double.NaN, Double.POSITIVE_INFINITY)) {
            YamlConfiguration config = config();
            config.set("update-interval-ticks", value);
            assertThrows(IllegalArgumentException.class, () -> Settings.read(config), "value=" + value);
        }
    }

    @Test
    void rejectsInvalidScalesAndExcessiveLines() {
        YamlConfiguration config = config();
        config.set("display-scale", -1);
        assertThrows(IllegalArgumentException.class, () -> Settings.read(config));
        config.set("display-scale", 1);
        config.set("display-text", java.util.Collections.nCopies(17, "строка"));
        assertThrows(IllegalArgumentException.class, () -> Settings.read(config));
    }

    @Test
    void rejectsMisspelledBooleans() {
        YamlConfiguration config = config();
        config.set("hide-vanilla-names", "flase");
        assertThrows(IllegalArgumentException.class, () -> Settings.read(config));
    }
}
