package com.radio.shell;

import com.radio.player.AudioPlayer;
import com.radio.util.UIUtils;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.shell.core.InputReader;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandExecutor;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ParsedInput;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

@Component
public class InteractiveShell implements ApplicationRunner {

    private static final String ANSI_CYAN   = "\033[36m";
    private static final String ANSI_GREEN  = "\033[32m";
    private static final String ANSI_YELLOW = "\033[33m";
    private static final String ANSI_RESET  = "\033[0m";
    private static final String ANSI_BOLD   = "\033[1m";

    private final CommandParser commandParser;
    private final CommandRegistry commandRegistry;
    private final CommandExecutor commandExecutor;
    private final AudioPlayer player;

    public InteractiveShell(CommandParser commandParser,
                            CommandRegistry commandRegistry,
                            AudioPlayer player) {
        this.commandParser = commandParser;
        this.commandRegistry = commandRegistry;
        this.commandExecutor = new CommandExecutor(commandRegistry);
        this.player = player;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        // Simple InputReader backed by our BufferedReader
        InputReader inputReader = new InputReader() {
            @Override
            public String readInput() throws Exception {
                return reader.readLine();
            }
        };

        printBanner(out);

        String line;
        while (true) {
            out.print(getPrompt());
            out.flush();

            line = reader.readLine();
            if (line == null) break; // EOF (Ctrl+D)

            line = line.trim();
            if (line.isEmpty()) continue;

            if (isExitCommand(line)) {
                player.stop();
                out.println("  " + ANSI_YELLOW + "Görüşmek üzere! ♬" + ANSI_RESET);
                break;
            }

            if (isHelpCommand(line)) {
                printHelp(out);
                continue;
            }

            executeCommand(line, out, inputReader);
        }
    }

    private void executeCommand(String input, PrintWriter out, InputReader inputReader) {
        try {
            ParsedInput parsedInput = commandParser.parse(input);
            CommandContext ctx = new CommandContext(parsedInput, commandRegistry, out, inputReader);
            commandExecutor.execute(ctx);
            out.flush();
        } catch (org.springframework.shell.core.command.CommandNotFoundException e) {
            out.println("  ⚠ '" + input.split(" ")[0] + "' komutu bulunamadı. 'help' ile komutları görün.");
        } catch (Exception e) {
            String msg = e.getMessage();
            out.println("  ⚠ " + (msg != null ? msg : e.getClass().getSimpleName()));
        }
    }

    private boolean isExitCommand(String line) {
        return line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit") ||
               line.equalsIgnoreCase("çıkış") || line.equalsIgnoreCase("cikis") ||
               line.equals("q");
    }

    private boolean isHelpCommand(String line) {
        return line.equalsIgnoreCase("help") || line.equalsIgnoreCase("yardim") ||
               line.equalsIgnoreCase("yardım") || line.equals("?");
    }

    private String getPrompt() {
        if (player.isPlaying() && player.getCurrentStation() != null) {
            return ANSI_GREEN + ANSI_BOLD + "♬ [" + player.getCurrentStation().name() + "] "
                    + ANSI_RESET + ANSI_CYAN + ANSI_BOLD + "radio> " + ANSI_RESET;
        }
        return ANSI_CYAN + ANSI_BOLD + "radio> " + ANSI_RESET;
    }

    private void printBanner(PrintWriter out) {
        out.println();
        String[] lines = {
            "♬  ░░░ RADIO SHELL ░░░  ♬",
            "Terminal FM Radio Player - Türkiye & Dünya",
            "v1.0.0 | Spring Boot 4 + Java 25"
        };
        UIUtils.printBoxed(out, lines, 60, ANSI_CYAN + ANSI_BOLD);
        out.println();
        out.println("  " + ANSI_YELLOW + "Komutlar için 'help', çıkmak için 'exit' yazın." + ANSI_RESET);
        out.println();
    }

    private void printHelp(PrintWriter out) {
        out.println();
        String[] lines = { "KOMUT LİSTESİ" };
        UIUtils.printBoxed(out, lines, 60, ANSI_CYAN);
        out.println();
        out.println("  " + ANSI_BOLD + "İSTASYON LİSTELEME" + ANSI_RESET);
        out.println("    listele              - Tüm istasyonları listeler");
        out.println("    turkiye              - Türkiye istasyonlarını listeler");
        out.println("    ulkeler              - Mevcut ülkeleri listeler");
        out.println("    ulke -i <ülke>       - Belirli ülke istasyonları");
        out.println("    turler               - Müzik türlerini listeler");
        out.println("    tur -i <tür>         - Belirli türdeki istasyonlar");
        out.println("    ara -s <arama>       - İstasyon arama");
        out.println();
        out.println("  " + ANSI_BOLD + "OYNATMA" + ANSI_RESET);
        out.println("    cal -i <id>          - İstasyonu çalar (ID veya isim)");
        out.println("    dur                  - Çalmayı durdurur");
        out.println("    durum                - Şu an çalanı gösterir");
        out.println("    ses -s <0-100>       - Ses seviyesini ayarlar");
        out.println();
        out.println("  " + ANSI_BOLD + "FAVORİLER" + ANSI_RESET);
        out.println("    favori -i <id>       - Favorilere ekle/çıkar");
        out.println("    favoriler            - Favori listesi");
        out.println();
        out.println("  " + ANSI_BOLD + "YÖNETİM" + ANSI_RESET);
        out.println("    ekle --id <id> --isim <isim> --ulke <ülke> --tur <tür> --url <url>");
        out.println("    sil --id <id>        - Özel istasyonu siler");
        out.println();
        out.println("  " + ANSI_BOLD + "DİĞER" + ANSI_RESET);
        out.println("    help / ?             - Bu yardım menüsü");
        out.println("    exit / q             - Çıkış");
        out.println();
        out.println("  " + ANSI_YELLOW + "Örnek: cal -i tr-powerfm" + ANSI_RESET);
        out.println();
    }
}
