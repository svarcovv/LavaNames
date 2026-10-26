package dev.svarcovv.lavanames;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextFormat {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern CODES = Pattern.compile(
            "(?i)[&§]x(?:[&§][0-9a-f]){6}|[&§]#[0-9a-f]{6}|[&§][0-9a-fk-or]");
    private static final String[] COLORS = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red",
            "light_purple", "yellow", "white"};

    private TextFormat() {}

    public static Component parse(String text) {
        Matcher matcher = CODES.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group().toLowerCase(java.util.Locale.ROOT);
            char key = code.charAt(1);
            String replacement;
            if (key == 'x') {
                StringBuilder hex = new StringBuilder();
                for (int i = 3; i < code.length(); i += 2) {
                    hex.append(code.charAt(i));
                }
                replacement = "<reset><#" + hex + ">";
            } else if (key == '#') {
                replacement = "<reset><" + code.substring(1) + ">";
            } else {
                int color = Character.digit(key, 16);
                replacement = color >= 0 ? "<reset><" + COLORS[color] + ">" : switch (key) {
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    default -> "<reset>";
                };
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return MINI.deserialize(result.toString()).decoration(TextDecoration.ITALIC, false);
    }
}
