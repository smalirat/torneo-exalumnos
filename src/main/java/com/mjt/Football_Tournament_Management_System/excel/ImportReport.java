package com.mjt.Football_Tournament_Management_System.excel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an import does (or would do, in a preview): per-entity counters, the list of changes, conflicts with data
 * edited on the web, warnings (inconsistencies of the Excel) and notices (rows skipped on purpose).
 */
public class ImportReport {
    public record Counter(String entity, int created, int updated, int unchanged, int conflicts) {}

    /** A record edited on the web whose value differs from the Excel; skipped unless its key is in `overwrite`. */
    public record Conflict(String key, String entity, String description, String excelValue, String webValue,
                           boolean overwritten) {}

    public record Change(String entity, String action, String description) {}

    private final Map<String, int[]> counters = new LinkedHashMap<>();
    public final List<Change> changes = new ArrayList<>();
    public final List<Conflict> conflicts = new ArrayList<>();
    public final List<String> warnings = new ArrayList<>();
    public final List<String> notices = new ArrayList<>();

    public void created(String entity, String description) {
        count(entity)[0]++;
        changes.add(new Change(entity, "CREATE", description));
    }

    public void updated(String entity, String description) {
        count(entity)[1]++;
        changes.add(new Change(entity, "UPDATE", description));
    }

    public void unchanged(String entity) {
        count(entity)[2]++;
    }

    public void conflict(String key, String entity, String description, String excelValue, String webValue,
                         boolean overwritten) {
        count(entity)[3]++;
        conflicts.add(new Conflict(key, entity, description, excelValue, webValue, overwritten));
    }

    public List<Counter> counters() {
        return counters.entrySet().stream()
                .map(e -> new Counter(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    /** One-line summary stored with the import history. */
    public String summary() {
        var sb = new StringBuilder();
        for (var c : counters()) {
            if (c.created() + c.updated() + c.conflicts() == 0) continue;
            sb.append(c.entity()).append(": ").append(c.created()).append(" nuevos, ").append(c.updated())
                    .append(" actualizados");
            if (c.conflicts() > 0) sb.append(", ").append(c.conflicts()).append(" conflictos");
            sb.append(". ");
        }
        if (!warnings.isEmpty()) sb.append(warnings.size()).append(" advertencias.");
        var s = sb.toString().trim();
        return s.isEmpty() ? "Sin cambios." : s.length() > 1900 ? s.substring(0, 1900) + "…" : s;
    }

    private int[] count(String entity) {
        return counters.computeIfAbsent(entity, k -> new int[4]);
    }
}
