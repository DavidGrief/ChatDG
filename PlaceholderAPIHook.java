package ru.davidgrief.chatdg.color;

import ru.davidgrief.chatdg.compatibility.VersionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern GRADIENT = Pattern.compile("<gradient:#([A-Fa-f0-9]{6}):#([A-Fa-f0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);
    private static final Pattern AMP_HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern ANGLE_HEX = Pattern.compile("<##([A-Fa-f0-9]{6})>");
    private static final Pattern PLAIN_HEX = Pattern.compile("(?<![&§#])#([A-Fa-f0-9]{6})");
    private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRr";
    private static final int[][] LEGACY_RGB = {
            {0x000000, '0'}, {0x0000AA, '1'}, {0x00AA00, '2'}, {0x00AAAA, '3'},
            {0xAA0000, '4'}, {0xAA00AA, '5'}, {0xFFAA00, '6'}, {0xAAAAAA, '7'},
            {0x555555, '8'}, {0x5555FF, '9'}, {0x55FF55, 'a'}, {0x55FFFF, 'b'},
            {0xFF5555, 'c'}, {0xFF55FF, 'd'}, {0xFFFF55, 'e'}, {0xFFFFFF, 'f'}
    };

    private final VersionManager versionManager;
    private volatile boolean legacyEnabled = true;
    private volatile boolean rgbEnabled = true;
    private volatile boolean rgbFallback = true;

    public ColorUtil(VersionManager versionManager) {
        this.versionManager = versionManager;
    }

    public void configure(boolean legacy, boolean rgb, boolean fallback) {
        this.legacyEnabled = legacy;
        this.rgbEnabled = rgb;
        this.rgbFallback = fallback;
    }

    public String colorize(String input) {
        if (input == null || input.isEmpty()) return input == null ? "" : input;
        String value = applyGradients(input);
        value = replaceHex(value, AMP_HEX);
        value = replaceHex(value, ANGLE_HEX);
        value = replaceHex(value, PLAIN_HEX);
        if (legacyEnabled) value = translateLegacy(value);
        return value;
    }

    public String stripUserColors(String input) {
        return sanitizeUserColors(input, false, false);
    }

    public String sanitizeUserColors(String input, boolean allowLegacy, boolean allowRgb) {
        if (input == null) return "";
        String value = input;
        if (!allowRgb) {
            value = AMP_HEX.matcher(value).replaceAll("");
            value = ANGLE_HEX.matcher(value).replaceAll("");
            value = PLAIN_HEX.matcher(value).replaceAll("");
            value = value.replaceAll("(?i)<#[A-F0-9]{6}>", "");
            value = value.replaceAll("(?i)<gradient:#[A-F0-9]{6}:#[A-F0-9]{6}>", "");
            value = value.replaceAll("(?i)</gradient>", "");
        }
        if (!allowLegacy) {
            value = value.replaceAll("(?i)&[0-9A-FK-OR]", "");
            value = value.replaceAll("(?i)§[0-9A-FK-OR]", "");
        }
        return value;
    }

    private String applyGradients(String input) {
        Matcher matcher = GRADIENT.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            int start = Integer.parseInt(matcher.group(1), 16);
            int end = Integer.parseInt(matcher.group(2), 16);
            String text = matcher.group(3);
            String gradient = buildGradient(start, end, text);
            matcher.appendReplacement(out, Matcher.quoteReplacement(gradient));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String buildGradient(int start, int end, String text) {
        if (text.isEmpty()) return "";
        List<Integer> codePoints = new ArrayList<Integer>();
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i);
            codePoints.add(cp);
            i += Character.charCount(cp);
        }
        StringBuilder out = new StringBuilder(text.length() * 8);
        int sr = (start >> 16) & 255, sg = (start >> 8) & 255, sb = start & 255;
        int er = (end >> 16) & 255, eg = (end >> 8) & 255, eb = end & 255;
        int count = codePoints.size();
        for (int i = 0; i < count; i++) {
            double t = count <= 1 ? 0D : (double) i / (double) (count - 1);
            int r = (int) Math.round(sr + (er - sr) * t);
            int g = (int) Math.round(sg + (eg - sg) * t);
            int b = (int) Math.round(sb + (eb - sb) * t);
            out.append(toColor(r, g, b)).appendCodePoint(codePoints.get(i));
        }
        return out.toString();
    }

    private String replaceHex(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String replacement;
            if (!rgbEnabled) replacement = "";
            else {
                int rgb = Integer.parseInt(matcher.group(1), 16);
                replacement = toColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String toColor(int r, int g, int b) {
        if (versionManager.supportsHexColors()) {
            String hex = String.format(Locale.ROOT, "%02X%02X%02X", r, g, b);
            StringBuilder out = new StringBuilder("§x");
            for (int i = 0; i < hex.length(); i++) out.append('§').append(hex.charAt(i));
            return out.toString();
        }
        if (!rgbFallback) return "";
        int rgb = (r << 16) | (g << 8) | b;
        char closest = 'f';
        long distance = Long.MAX_VALUE;
        for (int[] entry : LEGACY_RGB) {
            int c = entry[0];
            int cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
            long d = (long)(r - cr) * (r - cr) + (long)(g - cg) * (g - cg) + (long)(b - cb) * (b - cb);
            if (d < distance) { distance = d; closest = (char) entry[1]; }
        }
        return "§" + closest;
    }

    private String translateLegacy(String input) {
        char[] chars = input.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && LEGACY_CODES.indexOf(chars[i + 1]) >= 0) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }
}
