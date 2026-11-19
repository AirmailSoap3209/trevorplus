package com.trevorplus.storage;

import com.trevorplus.dye.DyeLuck.DyeDrop;
import com.trevorplus.quests.Animal;
import com.trevorplus.quests.Outcome;
import com.trevorplus.quests.QuestRecord;
import com.trevorplus.quests.Rarity;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Crash-safe local storage. History is append-only JSON Lines (one flat record per line, so a
 * crash can at worst damage the last line, which readers skip). Small state files are written
 * to a temp file and moved into place atomically.
 */
public final class HistoryStore {
    private final Path dir;
    private final Consumer<String> log;
    private final Set<Path> newlineChecked = new HashSet<>();
    private int skippedLines;

    public HistoryStore(Path dir, Consumer<String> log) {
        this.dir = dir;
        this.log = log;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.accept("storage: cannot create " + dir + ": " + e);
        }
    }

    public int skippedLines() { return skippedLines; }

    // ---- quests -----------------------------------------------------------------------------

    public synchronized void appendQuest(QuestRecord r, long sessionId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t0", r.startWallMs());
        m.put("t1", r.endWallMs());
        m.put("dur", r.durationMs());
        if (r.rarity() != null) m.put("rarity", r.rarity().name());
        if (r.areaName() != null) m.put("area", r.areaName());
        if (r.animal() != null) m.put("animal", r.animal().name());
        m.put("pelts", r.pelts());
        m.put("outcome", r.outcome().name());
        m.put("mult", r.multiplier());
        m.put("session", sessionId);
        appendLine("quests.jsonl", FlatJson.encode(m));
    }

    public synchronized List<QuestRecord> loadQuests() {
        List<QuestRecord> out = new ArrayList<>();
        for (Map<String, Object> m : readLines("quests.jsonl")) {
            try {
                out.add(new QuestRecord(
                        FlatJson.lng(m, "t0", 0), FlatJson.lng(m, "t1", 0), FlatJson.lng(m, "dur", -1),
                        Rarity.parse(FlatJson.str(m, "rarity", null)), FlatJson.str(m, "area", null),
                        animalOrNull(FlatJson.str(m, "animal", null)),
                        (int) FlatJson.lng(m, "pelts", 0), Outcome.valueOf(FlatJson.str(m, "outcome", "LOST")),
                        (int) FlatJson.lng(m, "mult", 1)));
            } catch (RuntimeException e) {
                skippedLines++;
                log.accept("storage: skipped bad quest record: " + e);
            }
        }
        return out;
    }

    private static Animal animalOrNull(String s) {
        if (s == null) return null;
        try {
            return Animal.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---- dyes and sessions ------------------------------------------------------------------

    public synchronized void appendDye(DyeDrop d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("t", d.wallMs());
        m.put("baseSince", d.baseQuestsSince());
        m.put("effSince", d.effectiveQuestsSince());
        m.put("chance", d.chanceBeforeDrop());
        m.put("mult", d.multiplier());
        appendLine("dyes.jsonl", FlatJson.encode(m));
    }

    public synchronized List<Map<String, Object>> loadDyes() { return readLines("dyes.jsonl"); }

    public synchronized void appendSession(Map<String, Object> summary) {
        appendLine("sessions.jsonl", FlatJson.encode(summary));
    }

    public synchronized List<Map<String, Object>> loadSessions() { return readLines("sessions.jsonl"); }

    // ---- small state files ------------------------------------------------------------------

    public synchronized void writeAtomic(String fileName, Map<String, Object> map) {
        Path target = dir.resolve(fileName);
        Path tmp = dir.resolve(fileName + ".tmp");
        try {
            Files.writeString(tmp, FlatJson.encode(map), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.accept("storage: cannot write " + fileName + ": " + e);
        }
    }

    /** Empty map when the file is missing or damaged. */
    public synchronized Map<String, Object> readJson(String fileName) {
        Path p = dir.resolve(fileName);
        if (!Files.exists(p)) return new LinkedHashMap<>();
        try {
            return FlatJson.decode(Files.readString(p, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            log.accept("storage: ignoring unreadable " + fileName + ": " + e);
            return new LinkedHashMap<>();
        }
    }

    // ---- internals --------------------------------------------------------------------------

    private void appendLine(String fileName, String line) {
        Path p = dir.resolve(fileName);
        try {
            String prefix = "";
            if (newlineChecked.add(p) && Files.exists(p) && !endsWithNewline(p)) prefix = "\n";
            Files.writeString(p, prefix + line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.accept("storage: cannot append to " + fileName + ": " + e);
        }
    }

    /** A crash can leave a last line without newline; make sure the next record starts fresh. */
    private static boolean endsWithNewline(Path p) throws IOException {
        try (RandomAccessFile f = new RandomAccessFile(p.toFile(), "r")) {
            if (f.length() == 0) return true;
            f.seek(f.length() - 1);
            return f.read() == '\n';
        }
    }

    private List<Map<String, Object>> readLines(String fileName) {
        Path p = dir.resolve(fileName);
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.exists(p)) return out;
        try {
            for (String line : Files.readAllLines(p, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                try {
                    out.add(FlatJson.decode(line));
                } catch (RuntimeException e) {
                    skippedLines++;
                    log.accept("storage: skipped damaged line in " + fileName);
                }
            }
        } catch (IOException e) {
            log.accept("storage: cannot read " + fileName + ": " + e);
        }
        return out;
    }
}
