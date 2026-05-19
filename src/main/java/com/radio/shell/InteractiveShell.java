package com.radio.shell;

import com.radio.player.AudioPlayer;
import com.radio.service.StationService;
import com.radio.util.Theme;
import com.radio.util.ThemeManager;
import com.radio.util.UIUtils;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.history.DefaultHistory;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.Status;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.shell.core.InputReader;
import org.springframework.shell.core.command.CommandContext;
import org.springframework.shell.core.command.CommandExecutor;
import org.springframework.shell.core.command.CommandParser;
import org.springframework.shell.core.command.CommandRegistry;
import org.springframework.shell.core.command.ParsedInput;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class InteractiveShell implements ApplicationRunner {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private final CommandParser commandParser;
    private final CommandRegistry commandRegistry;
    private final CommandExecutor commandExecutor;
    private final AudioPlayer player;
    private final StationService stationService;
    private final ThemeManager themeManager;
    private volatile boolean statusAnimationRunning;
    private Thread statusAnimationThread;

    public InteractiveShell(CommandParser commandParser,
                            CommandRegistry commandRegistry,
                            AudioPlayer player,
                            StationService stationService,
                            ThemeManager themeManager) {
        this.commandParser = commandParser;
        this.commandRegistry = commandRegistry;
        this.commandExecutor = new CommandExecutor(commandRegistry);
        this.player = player;
        this.stationService = stationService;
        this.themeManager = themeManager;
    }

    private Theme theme() {
        return themeManager.getCurrent();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // History file: ~/.radio-shell/history
        Path historyDir = Path.of(System.getProperty("user.home"), ".radio-shell");
        Files.createDirectories(historyDir);
        Path historyFile = historyDir.resolve("history");

        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .encoding(StandardCharsets.UTF_8)
                .build();

        // Built-in commands for completion
        List<String> builtinCommands = List.of(
                "help", "yardim", "yardım", "?",
                "exit", "quit", "çıkış", "cikis", "q"
        );

        // Commands that accept a station ID as an option value, mapped to their option flag(s)
        Set<String> stationIdOptions = Set.of("-i", "--istasyon", "--id");

        // Completer that combines registered commands + built-in commands + value completions
        Completer completer = (reader, line, candidates) -> {
            String word = line.word();
            int wordIndex = line.wordIndex();

            if (wordIndex == 0) {
                // Complete command names
                commandRegistry.getCommands().forEach(cmd -> {
                    if (cmd.getName().startsWith(word)) {
                        candidates.add(new Candidate(cmd.getName(), cmd.getName(),
                                null, cmd.getDescription(), null, null, true));
                    }
                });
                builtinCommands.forEach(cmd -> {
                    if (cmd.startsWith(word)) {
                        candidates.add(new Candidate(cmd));
                    }
                });
            } else {
                String cmdName = line.words().getFirst();
                String prevWord = wordIndex >= 1 ? line.words().get(wordIndex - 1) : null;
                String query = word.toLowerCase(TR);

                if (prevWord != null && stationIdOptions.contains(prevWord)
                        && Set.of("cal", "favori", "sil", "duzenle", "kontrol").contains(cmdName)) {
                    // Station ID value completion — contains match on ID or name
                    stationService.getAllStations().stream()
                            .filter(s -> s.id().toLowerCase(TR).contains(query)
                                    || s.name().toLowerCase(TR).contains(query))
                            .sorted(Comparator
                                    .<com.radio.model.RadioStation, Boolean>comparing(
                                            s -> !s.id().toLowerCase(TR).startsWith(query))
                                    .thenComparing(Comparator.comparing(s -> s.id().toLowerCase(TR))))
                            .forEach(s -> {
                                String display = s.id();
                                String descr = s.name() + (s.favorite() ? " ★" : "");
                                candidates.add(new Candidate(s.id(), display,
                                        s.country(), descr, null, null, true));
                            });

                } else if (prevWord != null && (prevWord.equals("-i") || prevWord.equals("--isim"))
                        && cmdName.equals("ulke")) {
                    // Country name completion
                    stationService.getCountries().stream()
                            .filter(c -> c.toLowerCase(TR).contains(query))
                            .sorted(Comparator
                                    .<String, Boolean>comparing(c -> !c.toLowerCase(TR).startsWith(query))
                                    .thenComparing(Comparator.comparing(c -> c.toLowerCase(TR))))
                            .forEach(c -> candidates.add(new Candidate(c, c, null,
                                    stationService.getStationsByCountry(c).size() + " istasyon",
                                    null, null, true)));

                } else if (prevWord != null && (prevWord.equals("-i") || prevWord.equals("--isim"))
                        && cmdName.equals("tur")) {
                    // Genre name completion
                    stationService.getGenres().stream()
                            .filter(g -> g.toLowerCase(TR).contains(query))
                            .sorted(Comparator
                                    .<String, Boolean>comparing(g -> !g.toLowerCase(TR).startsWith(query))
                                    .thenComparing(Comparator.comparing(g -> g.toLowerCase(TR))))
                            .forEach(g -> candidates.add(new Candidate(g)));

                } else if (prevWord != null && (prevWord.equals("-i") || prevWord.equals("--isim"))
                        && cmdName.equals("tema")) {
                    // Theme name completion
                    Theme.all().values().stream()
                            .filter(t -> t.name().startsWith(query))
                            .forEach(t -> candidates.add(new Candidate(t.name(), t.name(),
                                    null, t.description(), null, null, true)));

                } else {
                    // Complete option flags for the current command
                    var cmd = commandRegistry.getCommandByName(cmdName);
                    if (cmd != null && word.startsWith("-")) {
                        cmd.getOptions().forEach(opt -> {
                            String longOpt = "--" + opt.longName();
                            String shortOpt = "-" + opt.shortName();
                            if (longOpt.startsWith(word)) {
                                candidates.add(new Candidate(longOpt, longOpt,
                                        null, opt.description(), null, null, true));
                            }
                            if (shortOpt.startsWith(word)) {
                                candidates.add(new Candidate(shortOpt, shortOpt,
                                        null, opt.description(), null, null, true));
                            }
                        });
                    }
                }
            }
        };

        LineReader lineReader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .history(new DefaultHistory())
                .variable(LineReader.HISTORY_FILE, historyFile)
                .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                .build();

        PrintWriter out = terminal.writer();

        // InputReader backed by JLine
        InputReader inputReader = new InputReader() {
            @Override
            public String readInput() throws Exception {
                return lineReader.readLine();
            }
        };

        startPlaybackStatusAnimation(lineReader, terminal);
        try {
            printBanner(out);

            while (true) {
                String line;
                try {
                    line = lineReader.readLine(getPrompt());
                } catch (EndOfFileException e) {
                    break; // Ctrl+D
                } catch (UserInterruptException e) {
                    continue; // Ctrl+C - ignore and show new prompt
                }

                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (isExitCommand(line)) {
                    player.stop();
                    out.println("  " + theme().accent() + "Görüşmek üzere! ♬" + theme().reset());
                    break;
                }

                if (isHelpCommand(line)) {
                    printHelp(out);
                    continue;
                }

                executeCommand(line, out, inputReader);
            }
        } finally {
            stopPlaybackStatusAnimation();
            terminal.close();
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
        var t = theme();
        if (player.isPlaying() && player.getCurrentStation() != null) {
            var station = player.getCurrentStation();
            var songInfo = player.getCurrentSongInfo();
            var song = songInfo != null ? songInfo.displayTitle() : player.getCurrentSongTitle();
            if (song != null && !song.isBlank()) {
                song = UIUtils.truncate(song, 45);
            }
            var info = station.name() + (song != null && !song.isBlank() ? " - " + song : "");
            return t.secondary() + t.bold() + UIUtils.playbackPromptFrame() + " [" + info + "] "
                    + t.reset() + t.primary() + t.bold() + "radio> " + t.reset();
        }
        return t.primary() + t.bold() + "radio> " + t.reset();
    }

    private void startPlaybackStatusAnimation(LineReader lineReader, Terminal terminal) {
        statusAnimationRunning = true;
        statusAnimationThread = new Thread(() -> {
            Status status = Status.getStatus(terminal);
            if (status == null) {
                return;
            }

            boolean visible = false;
            int frame = 0;
            try {
                while (statusAnimationRunning && !Thread.currentThread().isInterrupted()) {
                    if (player.isPlaying() && player.getCurrentStation() != null) {
                        if (lineReader.isReading()) {
                            status.update(List.of(
                                    AttributedString.EMPTY,
                                    AttributedString.fromAnsi(buildPlaybackStatusLine(frame), terminal)));
                            visible = true;
                            frame++;
                        }
                    } else if (visible) {
                        status.hide();
                        visible = false;
                    }
                    Thread.sleep(180L);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (visible) {
                    status.hide();
                }
                status.close();
            }
        }, "PlaybackStatusAnimation");
        statusAnimationThread.setDaemon(true);
        statusAnimationThread.start();
    }

    private void stopPlaybackStatusAnimation() {
        statusAnimationRunning = false;
        if (statusAnimationThread != null) {
            statusAnimationThread.interrupt();
            try {
                statusAnimationThread.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            statusAnimationThread = null;
        }
    }

    private String buildPlaybackStatusLine(int frame) {
        var station = player.getCurrentStation();
        if (station == null) {
            return "";
        }

        var parts = new ArrayList<String>();
        parts.add(UIUtils.playbackAnimationFrame(frame));
        parts.add(station.name());

        var songInfo = player.getCurrentSongInfo();
        var song = songInfo != null ? songInfo.displayTitle() : player.getCurrentSongTitle();
        if (song != null && !song.isBlank()) {
            parts.add(UIUtils.truncate(song, 40));
        } else if (player.shouldShowPendingSongInfo(Duration.ofSeconds(15))) {
            parts.add("şarkı bilgisi bekleniyor");
        }

        parts.add(station.country());
        parts.add(station.genre());

        var streamInfo = player.getCurrentStreamInfo();
        if (streamInfo != null) {
            appendStreamInfoParts(parts, streamInfo);
        }

        parts.add("ses %" + player.getVolume());
        parts.add("süre " + formatStatusDuration(player.getPlaybackElapsed()));

        if (player.isRecording() && player.getRecordFile() != null) {
            parts.add("kayıt " + player.getRecordFile().getFileName());
        }
        if (player.isSleepTimerActive()) {
            parts.add("uyku " + formatStatusDuration(player.getSleepRemaining()));
        }

        var t = theme();
        return "  " + t.secondary() + t.bold() + parts.getFirst() + t.reset()
                + " " + t.primary() + UIUtils.truncate(String.join(" | ", parts.subList(1, parts.size())), 160) + t.reset();
    }

    private void appendStreamInfoParts(List<String> parts, AudioPlayer.StreamInfo streamInfo) {
        if (streamInfo.codec() != null && !streamInfo.codec().isBlank()) {
            parts.add(streamInfo.codec().toUpperCase(Locale.ROOT));
        } else if (streamInfo.contentType() != null && !streamInfo.contentType().isBlank()) {
            parts.add(streamInfo.contentType());
        }
        if (streamInfo.bitrateKbps() != null) {
            parts.add(streamInfo.bitrateKbps() + " kbps");
        }
        String audioShape = formatAudioShape(streamInfo);
        if (!audioShape.isBlank()) {
            parts.add(audioShape);
        }
    }

    private String formatAudioShape(AudioPlayer.StreamInfo streamInfo) {
        var shape = new ArrayList<String>();
        if (streamInfo.sampleRateHz() != null) {
            shape.add(formatSampleRate(streamInfo.sampleRateHz()));
        }
        if (streamInfo.channels() != null && !streamInfo.channels().isBlank()) {
            shape.add(streamInfo.channels());
        }
        return String.join(" ", shape);
    }

    private String formatSampleRate(int sampleRateHz) {
        if (sampleRateHz % 1000 == 0) {
            return (sampleRateHz / 1000) + " kHz";
        }
        return String.format(Locale.ROOT, "%.1f kHz", sampleRateHz / 1000.0);
    }

    private String formatStatusDuration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) {
            return "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
        }
        return "%02d:%02d".formatted(minutes, remainingSeconds);
    }

    private void printBanner(PrintWriter out) {
        var t = theme();
        out.println();
        String[] lines = {
            "♬  ░░░ RADIO SHELL ░░░  ♬",
            "Terminal FM Radio Player - Türkiye & Dünya",
            "v2.0.0 | Spring Boot 4 + Java 21"
        };
        UIUtils.printBoxed(out, lines, 60, t.primary() + t.bold());
        out.println();
        out.println("  " + t.accent() + "Komutlar için 'help', çıkmak için 'exit' yazın." + t.reset());
        out.println();
    }

    private void printHelp(PrintWriter out) {
        var t = theme();
        out.println();
        String[] lines = { "KOMUT LİSTESİ" };
        UIUtils.printBoxed(out, lines, 60, t.primary());
        out.println();
        out.println("  " + t.bold() + "İSTASYON LİSTELEME" + t.reset());
        out.println("    listele              - Tüm istasyonları listeler");
        out.println("    turkiye              - Türkiye istasyonlarını listeler");
        out.println("    ulkeler              - Mevcut ülkeleri listeler");
        out.println("    ulke -i <ülke>       - Belirli ülke istasyonları");
        out.println("    turler               - Müzik türlerini listeler");
        out.println("    tur -i <tür>         - Belirli türdeki istasyonlar");
        out.println("    ara -s <arama>       - İstasyon arama");
        out.println();
        out.println("  " + t.bold() + "OYNATMA" + t.reset());
        out.println("    cal -i <id>          - İstasyonu çalar (ID veya isim)");
        out.println("    son                  - Son çalınan istasyonu çalar");
        out.println("    dur                  - Çalmayı durdurur");
        out.println("    durum                - Şu an çalanı ve şarkı bilgisini gösterir");
        out.println("    ses -s <0-100>       - Ses seviyesini ayarlar");
        out.println("    sonraki              - Listedeki sonraki istasyona geçer");
        out.println("    onceki               - Listedeki önceki istasyona geçer");
        out.println("    karistir             - Rastgele istasyon çalar");
        out.println("      -u <ülke>          - Belirli ülkeden rastgele");
        out.println("      -t <tür>           - Belirli türden rastgele");
        out.println("      -f                 - Favorilerden rastgele");
        out.println("    uyku -d <dakika>     - Süre sonunda oynatmayı durdurur");
        out.println("    uyku iptal           - Uyku zamanlayıcısını iptal eder");
        out.println("    gecmis               - Son görülen şarkı bilgilerini listeler");
        out.println();
        out.println("  " + t.bold() + "KAYIT" + t.reset());
        out.println("    kaydet               - Yayını MP3 olarak kaydetmeye başlar");
        out.println("    kayitdur             - Kaydı durdurur ve dosyayı kaydeder");
        out.println();
        out.println("  " + t.bold() + "FAVORİLER" + t.reset());
        out.println("    favori -i <id>       - Favorilere ekle/çıkar");
        out.println("    favoriler            - Favori listesi");
        out.println();
        out.println("  " + t.bold() + "İSTATİSTİK" + t.reset());
        out.println("    istatistik           - En çok dinlenen ilk 10 istasyon ve toplam süre");
        out.println("    istatistik -n <adet> - Gösterilecek istasyon sayısını belirler");
        out.println();
        out.println("  " + t.bold() + "ONLINE İSTASYONLAR" + t.reset());
        out.println("    online-ara -s <isim> - RadioBrowser.info'dan istasyon arar");
        out.println("      -u <ülke>          - Ülke filtresi (örn: Turkey)");
        out.println("      -t <tür>           - Tür filtresi (örn: pop, jazz)");
        out.println("      -n <adet>          - Sonuç sayısı (varsayılan: 15)");
        out.println("    online-ekle -n <#>   - Arama sonucundan istasyonu listeye ekler");
        out.println();
        out.println("  " + t.bold() + "YÖNETİM" + t.reset());
        out.println("    kontrol              - Tüm istasyonların URL'lerini kontrol eder");
        out.println("    kontrol -i <id>      - Belirli istasyonu kontrol eder");
        out.println("    ekle --id <id> --isim <isim> --ulke <ülke> --tur <tür> --url <url>");
        out.println("    duzenle --id <id>    - Özel istasyonu düzenler");
        out.println("    iceaktar -d <dosya>  - M3U/PLS playlist içe aktarır");
        out.println("    sil --id <id>        - Özel istasyonu siler");
        out.println("    tema                 - Renk temasını değiştirir");
        out.println("    bildirim             - Masaüstü bildirimlerini açar/kapatır");
        out.println();
        out.println("  " + t.bold() + "DİĞER" + t.reset());
        out.println("    help / ?             - Bu yardım menüsü");
        out.println("    exit / q             - Çıkış");
        out.println();
        out.println("  " + t.accent() + "Örnek: cal -i tr-powerfm" + t.reset());
        out.println();
    }
}
