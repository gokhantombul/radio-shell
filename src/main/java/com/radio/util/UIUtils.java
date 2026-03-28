package com.radio.util;

import java.io.PrintWriter;

public class UIUtils {

    private static final String ANSI_RESET = "\033[0m";

    public static void printBoxed(PrintWriter out, String[] lines, int innerWidth, String color) {
        String top    = "  ╔" + "═".repeat(innerWidth + 2) + "╗";
        String bottom = "  ╚" + "═".repeat(innerWidth + 2) + "╝";

        out.println(color + top + ANSI_RESET);
        for (String line : lines) {
            int visualWidth = getVisualWidth(line);
            int totalPadding = innerWidth - visualWidth;
            int leftPadding = totalPadding / 2;
            int rightPadding = totalPadding - leftPadding;

            out.print(color + "  ║ " + ANSI_RESET);
            out.print(" ".repeat(Math.max(0, leftPadding)));
            out.print(line);
            out.print(" ".repeat(Math.max(0, rightPadding)));
            out.println(color + " ║" + ANSI_RESET);
        }
        out.println(color + bottom + ANSI_RESET);
    }

    public static String getBoxedString(String[] lines, int innerWidth) {
        StringBuilder sb = new StringBuilder();
        String top    = "  ╔" + "═".repeat(innerWidth + 2) + "╗\n";
        String bottom = "  ╚" + "═".repeat(innerWidth + 2) + "╝\n";

        sb.append(top);
        for (String line : lines) {
            int visualWidth = getVisualWidth(line);
            int totalPadding = innerWidth - visualWidth;
            int leftPadding = totalPadding / 2;
            int rightPadding = totalPadding - leftPadding;

            sb.append("  ║ ");
            sb.append(" ".repeat(Math.max(0, leftPadding)));
            sb.append(line);
            sb.append(" ".repeat(Math.max(0, rightPadding)));
            sb.append(" ║\n");
        }
        sb.append(bottom);
        return sb.toString();
    }

    public static String padRight(String s, int targetWidth) {
        if (s == null) s = "";
        int currentWidth = getVisualWidth(s);
        if (currentWidth >= targetWidth) return s;
        return s + " ".repeat(targetWidth - currentWidth);
    }

    public static String truncate(String s, int maxVisualWidth) {
        if (s == null) return "";
        if (getVisualWidth(s) <= maxVisualWidth) return s;

        // Truncate based on visual width
        StringBuilder sb = new StringBuilder();
        int width = 0;
        for (int i = 0; i < s.length(); i++) {
            int codePoint = s.codePointAt(i);
            int charWidth = isWide(codePoint) ? 2 : 1;
            if (width + charWidth + 2 > maxVisualWidth) { // +2 for ".."
                break;
            }
            sb.appendCodePoint(codePoint);
            width += charWidth;
            if (Character.isHighSurrogate(s.charAt(i))) i++;
        }
        sb.append("..");
        return sb.toString();
    }

    public static int getVisualWidth(String s) {
        if (s == null) return 0;
        // Strip ANSI escape codes
        String stripped = s.replaceAll("\033\\[[0-9;]*m", "");
        int width = 0;
        for (int i = 0; i < stripped.length(); i++) {
            int codePoint = stripped.codePointAt(i);
            if (Character.isHighSurrogate(stripped.charAt(i))) {
                i++;
            }
            if (isWide(codePoint)) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }

    private static boolean isWide(int codePoint) {
        // Only truly wide characters (East Asian Width = W or F)
        return (codePoint >= 0x1100 && codePoint <= 0x115F) ||   // Hangul Jamo
               (codePoint >= 0x2E80 && codePoint <= 0x303E) ||   // CJK Radicals, Kangxi, CJK Symbols
               (codePoint >= 0x3041 && codePoint <= 0x33BF) ||   // Hiragana, Katakana, Bopomofo, CJK Compat
               (codePoint >= 0x3400 && codePoint <= 0x4DBF) ||   // CJK Unified Ext A
               (codePoint >= 0x4E00 && codePoint <= 0x9FFF) ||   // CJK Unified Ideographs
               (codePoint >= 0xA960 && codePoint <= 0xA97F) ||   // Hangul Jamo Extended-A
               (codePoint >= 0xAC00 && codePoint <= 0xD7FF) ||   // Hangul Syllables
               (codePoint >= 0xF900 && codePoint <= 0xFAFF) ||   // CJK Compatibility Ideographs
               (codePoint >= 0xFE30 && codePoint <= 0xFE6F) ||   // CJK Compatibility Forms
               (codePoint >= 0xFF01 && codePoint <= 0xFF60) ||   // Fullwidth Forms
               (codePoint >= 0xFFE0 && codePoint <= 0xFFE6) ||   // Fullwidth Signs
               (codePoint >= 0x1F300 && codePoint <= 0x1F9FF) || // Misc Symbols & Pictographs, Emoticons, etc.
               (codePoint >= 0x20000 && codePoint <= 0x2FA1F);   // CJK Unified Ext B-F, Compat Supplement
    }
}
