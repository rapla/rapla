package org.rapla.server.spring.graphql;

import java.text.Collator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.rapla.server.spring.graphql.ReservationGraphQLController.EventTemplate;

/**
 * PRD 104 Phase 1 (D2/D3) — computes the server-side grouping path for each template of the
 * "Neu aus Vorlage" picker. Token-prefix clustering: the first name token (course keys like
 * {@code TINF23B4}) forms semantic buckets, oversized buckets recurse into the next token;
 * where tokens don't split, alphabetic range chunks take over — the Swing
 * {@code BalancedHierarchicalMenu} mechanic (max {@code maxPerNode} per level, same separator
 * set). Output is a flat path per template id (root first, empty = ungrouped top level), NOT a
 * tree — see PRD 104 D2. Input must be the §12-filtered caller-visible list: range-bucket
 * labels depend on siblings, so paths are a snapshot of exactly this list.
 */
final class TemplatePathBuilder
{
    /** Swing {@code BalancedHierarchicalMenu.isSep} separator set. */
    private static final Pattern SEPARATORS = Pattern.compile("[\\s/\\-_|:,·]+");

    private TemplatePathBuilder() {}

    static Map<String, List<String>> paths(List<EventTemplate> templates, Locale locale, int maxPerNode)
    {
        Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);
        List<EventTemplate> sorted = new ArrayList<>(templates);
        sorted.sort((a, b) -> collator.compare(a.name(), b.name()));
        Map<String, List<String>> out = new HashMap<>();
        recurse(sorted, 0, List.of(), Math.max(2, maxPerNode), out);
        return out;
    }

    private static void recurse(List<EventTemplate> items, int depth, List<String> prefix,
            int maxPerNode, Map<String, List<String>> out)
    {
        if (items.size() <= maxPerNode)
        {
            items.forEach(t -> out.put(t.id(), prefix));
            return;
        }

        Map<String, List<EventTemplate>> byToken = new LinkedHashMap<>();
        for (EventTemplate t : items)
        {
            String tok = token(t.name(), depth);
            byToken.computeIfAbsent(tok == null ? null : tok.toLowerCase(), k -> new ArrayList<>()).add(t);
        }

        if (byToken.size() <= 1)
        {
            if (byToken.containsKey(null))
            {
                rangeChunks(items, prefix, maxPerNode, out);   // no tokens left to distinguish
            }
            else
            {
                recurse(items, depth + 1, prefix, maxPerNode, out);   // all share this token — skip the redundant level
            }
            return;
        }

        List<EventTemplate> direct = new ArrayList<>();
        List<Map.Entry<String, List<EventTemplate>>> groups = new ArrayList<>();
        for (Map.Entry<String, List<EventTemplate>> e : byToken.entrySet())
        {
            if (e.getKey() == null || e.getValue().size() == 1)
            {
                direct.addAll(e.getValue());
            }
            else
            {
                groups.add(e);
            }
        }

        for (Map.Entry<String, List<EventTemplate>> e : groups)
        {
            String label = token(e.getValue().get(0).name(), depth);   // original spelling of the first member
            List<String> path = append(prefix, label);
            recurse(e.getValue(), depth + 1, path, maxPerNode, out);
        }
        if (direct.size() <= maxPerNode)
        {
            direct.forEach(t -> out.put(t.id(), prefix));
        }
        else
        {
            rangeChunks(direct, prefix, maxPerNode, out);
        }
    }

    /** Balanced alphabetic chunks with Swing-style range labels ("Raumplan10 – Raumplan21"). */
    private static void rangeChunks(List<EventTemplate> sorted, List<String> prefix, int maxPerNode,
            Map<String, List<String>> out)
    {
        int buckets = (sorted.size() + maxPerNode - 1) / maxPerNode;
        int size = (sorted.size() + buckets - 1) / buckets;
        for (int start = 0; start < sorted.size(); start += size)
        {
            List<EventTemplate> slice = sorted.subList(start, Math.min(start + size, sorted.size()));
            String label = rangeLabel(slice.get(0).name(), slice.get(slice.size() - 1).name());
            List<String> path = append(prefix, label);
            slice.forEach(t -> out.put(t.id(), path));
        }
    }

    private static String rangeLabel(String first, String last)
    {
        int lcp = 0;
        int limit = Math.min(first.length(), last.length());
        while (lcp < limit && Character.toLowerCase(first.charAt(lcp)) == Character.toLowerCase(last.charAt(lcp)))
        {
            lcp++;
        }
        if (lcp >= 3)
        {
            String anchor = first.substring(0, Math.min(lcp, 20));
            return anchor + distinguish(first, lcp) + " – " + anchor + distinguish(last, lcp);
        }
        return shorten(first) + " – " + shorten(last);
    }

    /** The first distinguishing segment after the common prefix. */
    private static String distinguish(String s, int from)
    {
        String rest = s.substring(Math.min(from, s.length()));
        String[] segments = SEPARATORS.split(rest.strip(), 2);
        return segments.length > 0 ? segments[0] : rest;
    }

    private static String shorten(String s)
    {
        return s.length() <= 14 ? s : s.substring(0, 14).strip() + "…";
    }

    private static String token(String name, int index)
    {
        String[] tokens = SEPARATORS.split(name.strip());
        return index < tokens.length && !tokens[index].isEmpty() ? tokens[index] : null;
    }

    private static List<String> append(List<String> prefix, String label)
    {
        List<String> path = new ArrayList<>(prefix);
        path.add(label);
        return List.copyOf(path);
    }
}
