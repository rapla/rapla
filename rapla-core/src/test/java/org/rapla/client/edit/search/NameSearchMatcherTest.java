package org.rapla.client.edit.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 unit coverage of {@link NameSearchMatcher} (PRD 023 Phase 7).
 * Pins case / diacritic / multi-word / null / empty-query semantics so
 * the Swing widget can rely on stable filter behaviour as the UI grows.
 */
class NameSearchMatcherTest
{
    // ---------- empty / null query ----------

    @Test
    void nullQueryMatchesEverything()
    {
        assertTrue(NameSearchMatcher.matches("Room A66", null));
        assertTrue(NameSearchMatcher.matches("", null));
        assertTrue(NameSearchMatcher.matches(null, null));
    }

    @Test
    void emptyQueryMatchesEverything()
    {
        assertTrue(NameSearchMatcher.matches("Room A66", ""));
        assertTrue(NameSearchMatcher.matches("Room A66", "   "));
    }

    // ---------- null candidate ----------

    @Test
    void nullCandidateNeverMatchesNonEmptyQuery()
    {
        assertFalse(NameSearchMatcher.matches(null, "room"));
    }

    @Test
    void nullCandidateStillMatchesEmptyQuery()
    {
        assertTrue(NameSearchMatcher.matches(null, ""));
        assertTrue(NameSearchMatcher.matches(null, null));
    }

    // ---------- substring ----------

    @Test
    void substringMatch()
    {
        assertTrue(NameSearchMatcher.matches("Room A66", "Room"));
        assertTrue(NameSearchMatcher.matches("Room A66", "A66"));
        assertTrue(NameSearchMatcher.matches("Room A66", "om A"));
    }

    @Test
    void noSubstringNoMatch()
    {
        assertFalse(NameSearchMatcher.matches("Room A66", "B12"));
        assertFalse(NameSearchMatcher.matches("Room A66", "xyz"));
    }

    // ---------- case ----------

    @Test
    void matchIsCaseInsensitive()
    {
        assertTrue(NameSearchMatcher.matches("Room A66", "ROOM"));
        assertTrue(NameSearchMatcher.matches("Room A66", "room"));
        assertTrue(NameSearchMatcher.matches("ROOM A66", "Room"));
    }

    // ---------- diacritic folding ----------

    @Test
    void diacriticOnQueryMatchesPlainCandidate()
    {
        assertTrue(NameSearchMatcher.matches("muller", "müller"));
    }

    @Test
    void plainQueryMatchesDiacriticCandidate()
    {
        assertTrue(NameSearchMatcher.matches("Müller", "muller"));
        assertTrue(NameSearchMatcher.matches("Café Mocha", "cafe"));
        assertTrue(NameSearchMatcher.matches("Niño", "nino"));
    }

    @Test
    void germanUmlautsFoldConsistently()
    {
        // Test all common German diacritics — the typical end-user case.
        assertTrue(NameSearchMatcher.matches("Übung", "ubung"));
        assertTrue(NameSearchMatcher.matches("Bär", "bar"));
        // ß folds to "ss" — typing "strasse" finds "Straße".
        assertTrue(NameSearchMatcher.matches("Straße", "strasse"));
        // ö folds to "o" (NFD-based); "grösse" → "grosse" → user types "grosse".
        // The German alternate-spelling form ("oe" / "ue" / "ae" for umlauts)
        // is NOT a standard Unicode normalization — we intentionally keep the
        // simpler NFD strip; users searching with diacritic letters or their
        // ASCII core both work.
        assertTrue(NameSearchMatcher.matches("Größe", "grosse"));
        assertTrue(NameSearchMatcher.matches("Größe", "größ"));
    }

    // ---------- multi-word ----------

    @Test
    void multiWordIsAnd()
    {
        assertTrue(NameSearchMatcher.matches("Big Conference Room 1", "big room"));
        assertTrue(NameSearchMatcher.matches("Big Conference Room 1", "room big"));   // order doesn't matter
        assertFalse(NameSearchMatcher.matches("Big Conference Room 1", "small room"));
    }

    @Test
    void multiWordCollapsesRepeatedWhitespace()
    {
        assertTrue(NameSearchMatcher.matches("Big Conference Room", "big      room"));
        assertTrue(NameSearchMatcher.matches("Big Conference Room", "  big   room  "));
    }

    @Test
    void multiWordWithDiacritics()
    {
        assertTrue(NameSearchMatcher.matches("Hôtel Café Müller", "muller cafe"));
        assertFalse(NameSearchMatcher.matches("Hôtel Café Müller", "muller absent"));
    }

    // ---------- prepared helpers (callers iterating many candidates) ----------

    @Test
    void preparedMatchEquivalentToMatches()
    {
        String[] prep = NameSearchMatcher.prepare("café room");
        assertTrue(NameSearchMatcher.matchesPrepared("Cafe Room 1", prep));
        assertTrue(NameSearchMatcher.matchesPrepared("Room Cafe Annex", prep));
        assertFalse(NameSearchMatcher.matchesPrepared("Room A66", prep));
        assertFalse(NameSearchMatcher.matchesPrepared(null, prep));
    }

    @Test
    void preparedEmptyMatchesAll()
    {
        String[] prep = NameSearchMatcher.prepare("");
        assertTrue(NameSearchMatcher.matchesPrepared("anything", prep));
        assertTrue(NameSearchMatcher.matchesPrepared(null, prep));
        assertEquals(0, prep.length);
    }
}
