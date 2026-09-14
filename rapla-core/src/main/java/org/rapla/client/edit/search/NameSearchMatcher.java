package org.rapla.client.edit.search;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java substring matcher used by the resource / allocatable picker
 * search field (PRD 023 Phase 7) and any other UI surface that wants a
 * forgiving "does this label contain what the user typed" check.
 * <p>
 * Semantics:
 * <ul>
 *   <li>Empty / null query → match-all (returns {@code true}).</li>
 *   <li>Multi-word query → AND of all words (whitespace-split).
 *       Order does not matter.</li>
 *   <li>Case-insensitive (Java root-locale lowercase).</li>
 *   <li>Diacritic-folded: {@code "Müller"} matches {@code "muller"},
 *       {@code "café"} matches {@code "cafe"}. Achieved via NFD
 *       normalization and stripping combining marks.</li>
 *   <li>Null candidate → no match.</li>
 * </ul>
 * <p>
 * Designed to be cheap enough for typing-into-a-filter use (no precompiled
 * regex per keystroke; the cost is one normalize+lowercase per candidate
 * per typed char). At a few thousand entries it stays sub-millisecond on
 * a dev laptop; if a deployment goes past 10k pickable items we'll add
 * a debounce on the Swing side, not algorithmic complexity here.
 */
public final class NameSearchMatcher
{
    private NameSearchMatcher() {}

    /**
     * @return {@code true} if {@code candidate} matches {@code query} per
     *         the semantics in the class doc.
     */
    public static boolean matches(String candidate, String query)
    {
        String[] terms = splitTerms(query);
        if (terms.length == 0) return true;          // empty query matches all
        if (candidate == null) return false;
        String folded = fold(candidate);
        for (String term : terms)
        {
            if (!folded.contains(term)) return false;
        }
        return true;
    }

    /**
     * Pre-split + fold helper for callers that want to test many
     * candidates against one query. Splits and folds the query once; each
     * subsequent {@link #matchesPrepared} skips that work.
     */
    public static String[] prepare(String query)
    {
        return splitTerms(query);
    }

    public static boolean matchesPrepared(String candidate, String[] preparedTerms)
    {
        if (preparedTerms.length == 0) return true;
        if (candidate == null) return false;
        String folded = fold(candidate);
        for (String term : preparedTerms)
        {
            if (!folded.contains(term)) return false;
        }
        return true;
    }

    /** Splits {@code query} on whitespace, folds each part, drops empties. */
    private static String[] splitTerms(String query)
    {
        if (query == null) return new String[0];
        String trimmed = query.strip();
        if (trimmed.isEmpty()) return new String[0];
        String[] raw = trimmed.split("\\s+");
        List<String> out = new ArrayList<>(raw.length);
        for (String r : raw)
        {
            String f = fold(r);
            if (!f.isEmpty()) out.add(f);
        }
        return out.toArray(new String[0]);
    }

    /** NFD-normalize, strip combining marks, lowercase under the root locale,
     *  and apply German {@code ß → ss} (NFD doesn't decompose eszett but a
     *  German user typing "strasse" expects "Straße" to match). */
    private static String fold(String s)
    {
        // Normalizer is allocation-free for already-NFC ASCII; minor work for diacritics.
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        // Strip combining marks (Unicode category Mn) — turns "ö" → "o", "ñ" → "n", etc.
        n = n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        n = n.toLowerCase(java.util.Locale.ROOT);
        // German ß stays through NFD; map to "ss" so "groesse" matches "Größe".
        if (n.indexOf('ß') >= 0) n = n.replace("ß", "ss");
        return n;
    }
}
