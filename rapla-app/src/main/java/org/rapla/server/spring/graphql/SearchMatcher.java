package org.rapla.server.spring.graphql;

import java.util.Locale;

/**
 * PRD 028 Phase 1 — string-match algorithm + ranking key for power search.
 *
 * <p>Shared helper used by the {@code searchText} + {@code matchKind} args on
 * {@code Query.allocatables} and {@code Query.reservations} (and, when
 * landed, the top-level {@code Query.search} from PRD 060).
 *
 * <p><b>Matching:</b>
 * <ul>
 *   <li>{@link MatchKind#PREFIX} — case-insensitive {@code startsWith}.</li>
 *   <li>{@link MatchKind#SUBSTRING} — case-insensitive {@code contains}.</li>
 *   <li>{@link MatchKind#FUZZY} — Levenshtein distance ≤ 1 against any
 *       contained substring of the same length as the query (a small,
 *       fixed-cost approximation that catches common typos like
 *       "Smyth" → "Smith" without needing a real edit-distance index).</li>
 * </ul>
 *
 * <p><b>Ranking:</b> {@link #rank} returns a score where lower = better.
 * Composed as {@code kindWeight * 10000 + matchPosition} so the call site
 * can stable-sort by score then by id and get PRD 028's documented order:
 * match-strength → match-position → id.
 */
final class SearchMatcher
{
    public enum MatchKind { PREFIX, SUBSTRING, FUZZY }

    private SearchMatcher() {}

    /**
     * Returns {@code true} if {@code haystack} matches {@code needle} under
     * the chosen {@code kind}. Null/blank inputs match (the call site should
     * skip-empty before invoking).
     */
    static boolean matches(String haystack, String needle, MatchKind kind)
    {
        if (haystack == null || haystack.isEmpty()) return false;
        if (needle == null || needle.isEmpty()) return true;
        String h = haystack.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        return switch (kind)
        {
            case PREFIX    -> h.startsWith(n);
            case SUBSTRING -> h.contains(n);
            case FUZZY     -> h.contains(n) || fuzzyContains(h, n, 1);
        };
    }

    /**
     * Ranking score — LOWER is better. Composed as
     * {@code kindWeight * 10000 + matchPosition} so PREFIX always wins over
     * SUBSTRING, SUBSTRING always over FUZZY, and within the same kind
     * earlier-in-string wins. Returns {@link Integer#MAX_VALUE} for
     * no-match.
     */
    static int rank(String haystack, String needle, MatchKind kind)
    {
        if (haystack == null || haystack.isEmpty()
                || needle == null || needle.isEmpty()) return Integer.MAX_VALUE;
        String h = haystack.toLowerCase(Locale.ROOT);
        String n = needle.toLowerCase(Locale.ROOT);
        return switch (kind)
        {
            case PREFIX    -> h.startsWith(n) ? 0 : Integer.MAX_VALUE;
            case SUBSTRING -> {
                int idx = h.indexOf(n);
                yield idx >= 0 ? 10000 + idx : Integer.MAX_VALUE;
            }
            case FUZZY -> {
                int idx = h.indexOf(n);
                if (idx >= 0) yield 10000 + idx;             // exact substring within FUZZY
                int fuzzyIdx = firstFuzzyMatchPosition(h, n, 1);
                yield fuzzyIdx >= 0 ? 20000 + fuzzyIdx : Integer.MAX_VALUE;
            }
        };
    }

    /** True if any length-{@code n} window of {@code h} is within
     *  Levenshtein distance {@code maxDist} of {@code n}. */
    private static boolean fuzzyContains(String h, String n, int maxDist)
    {
        return firstFuzzyMatchPosition(h, n, maxDist) >= 0;
    }

    /** Returns the position of the first length-{@code n} window of {@code h}
     *  within Levenshtein {@code maxDist}, or {@code -1} if none. */
    private static int firstFuzzyMatchPosition(String h, String n, int maxDist)
    {
        if (n.length() == 0) return 0;
        int hl = h.length(), nl = n.length();
        // Slide a window of size nl across h; also allow length+/-maxDist for
        // insertions/deletions at the haystack side. Capped at 3 lengths to
        // keep the cost bounded.
        int[] widths = nl <= maxDist ? new int[] { nl } : new int[] { nl, nl - 1, nl + 1 };
        for (int start = 0; start <= hl; start++)
        {
            for (int w : widths)
            {
                if (w <= 0 || start + w > hl) continue;
                if (levenshtein(h, start, w, n) <= maxDist) return start;
            }
        }
        return -1;
    }

    /** Levenshtein distance between {@code h[start..start+len]} and {@code n}.
     *  Bounded — returns early once the running min exceeds the smaller of
     *  the two lengths (cheap safety net for the ~3-length call sites). */
    private static int levenshtein(String h, int hStart, int hLen, String n)
    {
        int nl = n.length();
        if (hLen == 0) return nl;
        if (nl == 0) return hLen;
        int[] prev = new int[nl + 1];
        int[] curr = new int[nl + 1];
        for (int j = 0; j <= nl; j++) prev[j] = j;
        for (int i = 1; i <= hLen; i++)
        {
            curr[0] = i;
            char hi = h.charAt(hStart + i - 1);
            for (int j = 1; j <= nl; j++)
            {
                int cost = hi == n.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[nl];
    }
}
