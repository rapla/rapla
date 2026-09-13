package org.rapla.server.spring.patch;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * PRD 112 — the self-describing head of a deployment patch file: {@code {{! rapla-document …}}}
 * (a Mustache comment the renderer discards) or leading {@code # rapla-view …} lines (GraphQL
 * comments the parser discards). {@code key: value} per line; {@code updated} is ISO-8601 —
 * an offset/Z is honoured, a bare local datetime is read as UTC (the store's clock).
 */
public record FrontMatter(String kind, Map<String, String> values)
{
    public static final String DOCUMENT = "rapla-document";
    public static final String VIEW = "rapla-view";

    public enum Action { CREATE, UPDATE, SKIP }

    public static Optional<FrontMatter> parse(String text)
    {
        if (text == null) return Optional.empty();
        String head = text.stripLeading();
        if (head.startsWith("{{!"))
        {
            int end = head.indexOf("}}");
            if (end < 0) return Optional.empty();
            return of(head.substring(3, end).strip().split("\\R"));
        }
        if (head.startsWith("#"))
        {
            List<String> lines = new ArrayList<>();
            for (String line : head.split("\\R"))
            {
                if (!line.startsWith("#")) break;
                lines.add(line.substring(1).strip());
            }
            return of(lines.toArray(String[]::new));
        }
        return Optional.empty();
    }

    private static Optional<FrontMatter> of(String[] lines)
    {
        if (lines.length == 0) return Optional.empty();
        String kind = lines[0].strip();
        if (!kind.equals(DOCUMENT) && !kind.equals(VIEW)) return Optional.empty();
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++)
        {
            int colon = lines[i].indexOf(':');
            if (colon < 0) continue;
            values.put(lines[i].substring(0, colon).strip(), lines[i].substring(colon + 1).strip());
        }
        return Optional.of(new FrontMatter(kind, values));
    }

    public String value(String key)
    {
        return values.get(key);
    }

    public boolean isPublic()
    {
        return Boolean.parseBoolean(values.getOrDefault("public", "false"));
    }

    public List<String> groups()
    {
        String raw = values.get("groups");
        if (raw == null || raw.isBlank()) return List.of();
        List<String> groups = new ArrayList<>();
        for (String g : raw.split(",")) if (!g.isBlank()) groups.add(g.strip());
        return groups;
    }

    /** UTC. Null when the head carries no (parseable) {@code updated}. */
    public LocalDateTime updated()
    {
        String raw = values.get("updated");
        if (raw == null || raw.isBlank()) return null;
        try
        {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        }
        catch (DateTimeParseException ignored)
        {
            // fall through
        }
        try
        {
            return LocalDateTime.parse(raw);
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    /** The rule: missing → create; file newer than stored → update; else skip. */
    public static Action decide(LocalDateTime updated, Optional<LocalDateTime> storedLastChanged)
    {
        if (storedLastChanged.isEmpty()) return Action.CREATE;
        if (updated != null && updated.isAfter(storedLastChanged.get())) return Action.UPDATE;
        return Action.SKIP;
    }
}
