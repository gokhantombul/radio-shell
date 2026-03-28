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
        // Basic wide character detection for common symbols used in the app
        return (codePoint >= 0x1F300 && codePoint <= 0x1F9FF) || // Emojis
               (codePoint >= 0x2600 && codePoint <= 0x26FF) ||   // Symbols
               (codePoint >= 0x2700 && codePoint <= 0x27BF) ||   // Dingbats
               (codePoint >= 0x3000 && codePoint <= 0x9FFF) ||   // CJK
               (codePoint >= 0x2591 && codePoint <= 0x2593) ||   // Shades (░, ▒, ▓)
               (codePoint == 0x266C) || // ♬
               (codePoint == 0x2713) || // ✓
               (codePoint == 0x2717) || // ✗
               (codePoint == 0x2605) || // ★
               (codePoint == 0x2606);   // ☆
    }
}
