package dev.svarcovv.lavanames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextFormatTest {
    @Test
    void preservesAmpersandsAndMixesMiniMessageWithLegacy() {
        Component text = TextFormat.parse("<gold>☀ | </gold>&fТекст & сообщение<br><red>❤");
        assertEquals("☀ | Текст & сообщение\n❤", PlainTextComponentSerializer.plainText().serialize(text));
        assertTrue(parts(text).stream().anyMatch(part -> NamedTextColor.WHITE.equals(part.color())));
    }

    @Test
    void readsAllHexFormatsAndUppercaseCodes() {
        for (String code : List.of("&#AABBCC", "&x&A&A&B&B&C&C", "§x§A§A§B§B§C§C", "§#AABBCC")) {
            Component text = TextFormat.parse(code + "цвет");
            assertEquals("цвет", PlainTextComponentSerializer.plainText().serialize(text));
            assertTrue(parts(text).stream().anyMatch(part -> TextColor.color(0xAABBCC).equals(part.color())));
        }
    }

    @Test
    void legacyColorResetsPreviousDecorations() {
        Component text = TextFormat.parse("&lЖирный &fОбычный");
        Component normal = parts(text).stream().filter(part -> part instanceof net.kyori.adventure.text.TextComponent t
                && t.content().contains("Обычный")).findFirst().orElseThrow();
        assertNotEquals(TextDecoration.State.TRUE, normal.decoration(TextDecoration.BOLD));
    }

    private static List<Component> parts(Component component) {
        List<Component> parts = new ArrayList<>();
        parts.add(component);
        component.children().forEach(child -> parts.addAll(parts(child)));
        return parts;
    }
}
