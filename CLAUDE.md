# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Radio Shell** is a Spring Boot terminal-based FM radio player for Turkish and world radio stations. It provides an interactive CLI shell built with Spring Shell, processes JSON station data, manages user favorites, and streams audio via ffmpeg.

**Key constraints:**
- Java 25 required (uses preview features like record classes)
- ffmpeg must be installed for audio playback (not a build dependency, but runtime requirement)
- Spring Shell 4.0.1 for CLI interface
- Turkish language support (Locale-specific sorting/searching)

## Build & Run Commands

### Build
```bash
# Full build with tests
mvn clean package

# Build skipping tests (faster)
mvn clean package -DskipTests

# Compile only (no packaging)
mvn clean compile
```

### Run
```bash
# Direct terminal (recommended, has audio support)
./radio.sh

# Or manually from target JAR
java -jar target/radio-shell-1.0.0.jar

# Docker
./docker-run.sh          # Build (if needed) and run
./docker-run.sh build    # Force rebuild
docker compose up --build
```

### Tests
```bash
# Run all tests
mvn test

# Run single test class
mvn test -Dtest=RadioCommandsTest

# Run single test method
mvn test -Dtest=RadioCommandsTest#testPlayCommand
```

### IDE / Development
- **IntelliJ IDEA**: Use built-in Maven panel or right-click pom.xml → Run Maven → Commands
- **VS Code**: Install "Extension Pack for Java" and use Maven Explorer

## Architecture

### Layer Structure

**1. Entry Point** → `RadioShellApplication.java`
- Spring Boot application class with `@SpringBootApplication`
- Enables `RadioCommands` component via `@EnableCommand`

**2. Shell Layer** → `command/RadioCommands.java`
- Spring Shell `@Command` methods (listele, turkiye, cal, favori, etc.)
- Maps CLI input → service methods
- Formats output as tables/messages using `UIUtils`
- Supports options via `@Option` (e.g., `-i`, `--isim`)

**3. Business Logic** → `service/StationService.java`
- CRUD operations for radio stations
- Loads built-in stations from classpath `stations.json`
- Loads/saves user favorites and custom stations from `~/.radio-shell/`
- Implements search, filter by country/genre, toggle favorites
- Uses `ConcurrentHashMap` for thread-safe station storage

**4. Audio Playback** → `player/AudioPlayer.java`
- Manages ffplay process lifecycle (start, stop, set volume)
- Streams radio URLs via ProcessBuilder

**5. Configuration** → `config/RadioConfig.java` & `ShellConfig.java`
- `RadioConfig`: Loads properties (paths to favorites/custom stations)
- `ShellConfig`: Shutdown hooks for cleanup (e.g., kill ffplay process)

**6. Data Models** → `model/RadioStation.java` & `StationList.java`
- `RadioStation`: Java record (immutable, JSON-serializable)
- `StationList`: Wrapper for JSON array deserialization

### Data Flow

```
User Input (CLI)
    ↓
RadioCommands (parse options)
    ↓
StationService (search/filter stations in memory)
    ↓
AudioPlayer (spawn ffplay for streaming) OR format output table
    ↓
User Output (table/message/audio)
```

**Persistence:**
- Built-in stations: `src/main/resources/stations.json` (classpath, read-only)
- Custom stations: `~/.radio-shell/custom-stations.json` (user-added via `ekle` command)
- Favorites: `~/.radio-shell/favorites.json` (simple JSON array of IDs)

## Key Classes & Responsibilities

| Class | Path | Purpose |
|-------|------|---------|
| `RadioCommands` | `command/` | CLI command handlers (15+ commands) |
| `StationService` | `service/` | Station CRUD, search, favorites management |
| `AudioPlayer` | `player/` | ffplay process management |
| `RadioStation` | `model/` | Immutable station record (id, name, country, genre, url, favorite) |
| `RadioConfig` | `config/` | Reads application properties for file paths |
| `UIUtils` | `util/` | Box drawing, table formatting |

## Important Details

### Turkish Locale Handling
- Search & sorting use Turkish locale (`Locale.forLanguageTag("tr")`) for correct collation
- Station names/countries are Turkish (e.g., "Türkiye", "TRT FM")

### Favorites & Custom Stations
- Both stored in user's home directory `~/.radio-shell/`
- Directories created on-demand if missing
- Favorites are stored as simple JSON array of station IDs
- Custom stations preserve built-in stations on save (filtered out)

### Spring Shell Integration
- Commands auto-generate help (`help` command)
- Options use annotations: `@Option(longName="...", shortName='x', required=true)`
- Command group "Radio" for organization
- No explicit command registration needed (via `@EnableCommand`)

### Docker Multi-Stage Build
- Lightweight runtime image (JRE only)
- Volume mount: `~/.radio-shell` → `/root/.radio-shell` (persists favorites)
- Audio routing differs by OS: Linux (`/dev/snd` + PulseAudio), macOS (requires host PulseAudio or direct terminal run)

## Common Development Tasks

**Add a new command:**
1. Add `@Command` method to `RadioCommands.java`
2. Inject `StationService` (already available)
3. Use `formatStationTable()` or `UIUtils.getBoxedString()` for output
4. Test with `mvn test`

**Add a new search/filter:**
1. Add method to `StationService.java` (e.g., `getStationsByGenre()`)
2. Call it from a new or existing command
3. Handle empty results gracefully

**Modify station data:**
- Editing `stations.json` requires rebuild (`mvn clean package`)
- Users can add custom stations at runtime via `ekle` command

**Debug audio playback:**
- Check that ffmpeg is installed: `ffmpeg -version`
- Review `AudioPlayer.java` process logs
- Test URL directly: `ffplay "<url>"`

## Testing Notes

- Limited test coverage currently (focus on commands and service layer)
- Integration tests would need to mock `AudioPlayer` (subprocess spawning)
- Use Spring `@SpringBootTest` for service tests with real JSON loading
