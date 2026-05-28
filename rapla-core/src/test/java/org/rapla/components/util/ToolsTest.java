package org.rapla.components.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

@RunWith(JUnit4.class)
public class ToolsTest
{

    @Test
    public void testSplit() {
        String[] result = Tools.split("a;b2;c",';');
        Assert.assertEquals("a", result[0]);
        Assert.assertEquals("b2", result[1]);
        Assert.assertEquals("c", result[2]);
    }

    // === PRD 057 — isSpecCompliant ===

    @Test
    public void isSpecCompliant_acceptsAscii() {
        Assert.assertTrue(Tools.isSpecCompliant("foo"));
        Assert.assertTrue(Tools.isSpecCompliant("_foo"));
        Assert.assertTrue(Tools.isSpecCompliant("foo_bar_123"));
        Assert.assertTrue(Tools.isSpecCompliant("A1"));
        Assert.assertTrue(Tools.isSpecCompliant("_"));
    }

    @Test
    public void isSpecCompliant_rejectsLeadingDigit() {
        Assert.assertFalse(Tools.isSpecCompliant("1foo"));
    }

    @Test
    public void isSpecCompliant_rejectsHyphenSpaceDot() {
        Assert.assertFalse(Tools.isSpecCompliant("foo-bar"));
        Assert.assertFalse(Tools.isSpecCompliant("foo bar"));
        Assert.assertFalse(Tools.isSpecCompliant("foo.bar"));
    }

    @Test
    public void isSpecCompliant_rejectsUmlauts() {
        Assert.assertFalse(Tools.isSpecCompliant("Prüfer"));
        Assert.assertFalse(Tools.isSpecCompliant("Größe"));
    }

    @Test
    public void isSpecCompliant_rejectsReservedBooleanLiterals() {
        Assert.assertFalse(Tools.isSpecCompliant("true"));
        Assert.assertFalse(Tools.isSpecCompliant("false"));
    }

    @Test
    public void isSpecCompliant_rejectsEmptyAndNull() {
        Assert.assertFalse(Tools.isSpecCompliant(""));
        Assert.assertFalse(Tools.isSpecCompliant(null));
    }

    // === PRD 057 — toSpecKey ===

    @Test
    public void toSpecKey_passesThroughCleanInput() {
        Assert.assertEquals("foo", Tools.toSpecKey("foo", Collections.emptySet()));
        Assert.assertEquals("foo_bar", Tools.toSpecKey("foo_bar", Collections.emptySet()));
    }

    @Test
    public void toSpecKey_foldsGermanUmlauts() {
        Assert.assertEquals("Pruefer", Tools.toSpecKey("Prüfer", Collections.emptySet()));
        // Space + ( + ) → three underscores, two consecutive between extern.
        Assert.assertEquals("Pruefer__extern_", Tools.toSpecKey("Prüfer (extern)", Collections.emptySet()));
        Assert.assertEquals("Groesse", Tools.toSpecKey("Größe", Collections.emptySet()));
        Assert.assertEquals("Aepfel", Tools.toSpecKey("Äpfel", Collections.emptySet()));
    }

    @Test
    public void toSpecKey_foldsNfkdDiacritics() {
        Assert.assertEquals("cafe", Tools.toSpecKey("café", Collections.emptySet()));
        Assert.assertEquals("nino", Tools.toSpecKey("niño", Collections.emptySet()));
        Assert.assertEquals("naive", Tools.toSpecKey("naïve", Collections.emptySet()));
    }

    @Test
    public void toSpecKey_prefixesLeadingDigit() {
        Assert.assertEquals("_1foo", Tools.toSpecKey("1foo", Collections.emptySet()));
    }

    @Test
    public void toSpecKey_replacesInvalidChars() {
        Assert.assertEquals("foo_bar", Tools.toSpecKey("foo-bar", Collections.emptySet()));
        Assert.assertEquals("foo_bar", Tools.toSpecKey("foo bar", Collections.emptySet()));
        Assert.assertEquals("foo_bar", Tools.toSpecKey("foo.bar", Collections.emptySet()));
    }

    @Test
    public void toSpecKey_replacesNonLatinWithUnderscore() {
        // Cyrillic / Greek / CJK fall back to underscore — documented OOS for transliteration
        Assert.assertEquals("___", Tools.toSpecKey("абв", Collections.emptySet()));
    }

    @Test
    public void toSpecKey_emptyAndAllInvalidReturnsUnderscore() {
        Assert.assertEquals("_", Tools.toSpecKey("", Collections.emptySet()));
        Assert.assertEquals("_", Tools.toSpecKey(null, Collections.emptySet()));
    }

    @Test
    public void toSpecKey_resolvesCollisions() {
        Set<String> taken = new LinkedHashSet<>();
        taken.add("foo");
        Assert.assertEquals("foo_2", Tools.toSpecKey("foo", taken));
        taken.add("foo_2");
        Assert.assertEquals("foo_3", Tools.toSpecKey("foo", taken));
    }

    @Test
    public void toSpecKey_isAlwaysSpecCompliant() {
        // Property: whatever we feed in, the result must satisfy isSpecCompliant.
        String[] inputs = {
            "Prüfer", "1leading", "with space", "with-hyphen", "with.dot",
            "café", "niño", "", "абв", "_under", "ALLCAPS", "123", "ä", "ß"
        };
        for (String input : inputs)
        {
            String key = Tools.toSpecKey(input, Collections.emptySet());
            Assert.assertTrue("toSpecKey returned non-spec key '" + key + "' for input '" + input + "'",
                    Tools.isSpecCompliant(key));
        }
    }

    @Test
    public void makeValidKey_delegatesToSpecKey() {
        // makeValidKey kept as a back-compat alias for toSpecKey(..., emptySet()).
        Assert.assertEquals("Pruefer__extern_", Tools.makeValidKey("Prüfer (extern)"));
        Assert.assertEquals("foo_bar", Tools.makeValidKey("foo bar"));
    }
}
