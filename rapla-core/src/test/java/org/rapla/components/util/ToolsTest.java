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

    // === PRD 056 §9 — isValidEntityId (client-supplied id syntax) ===

    @Test
    public void isValidEntityId_acceptsUuidShapes() {
        Assert.assertTrue(Tools.isValidEntityId("e47ac10b-58cc-4372-a567-0e02b2c3d479"));
        Assert.assertTrue(Tools.isValidEntityId("f47ac10b-58cc-4372-a567-0e02b2c3d479"));
        Assert.assertTrue(Tools.isValidEntityId("r47ac10b-58cc-4372-a567-0e02b2c3d479")); // legacy letter
        Assert.assertTrue(Tools.isValidEntityId("abcd1234")); // 8 chars minimum
        Assert.assertTrue(Tools.isValidEntityId("a".repeat(64))); // 64 chars maximum
    }

    @Test
    public void isValidEntityId_rejectsBadLength() {
        Assert.assertFalse(Tools.isValidEntityId("abc1234"));          // 7 — too short
        Assert.assertFalse(Tools.isValidEntityId("a".repeat(65)));     // 65 — too long
        Assert.assertFalse(Tools.isValidEntityId(""));
        Assert.assertFalse(Tools.isValidEntityId(null));
    }

    @Test
    public void isValidEntityId_rejectsBadCharset() {
        Assert.assertFalse(Tools.isValidEntityId("e47ac10b;58cc4372"));  // ';' breaks conflict composite ids
        Assert.assertFalse(Tools.isValidEntityId("e47ac10b 58cc4372"));  // whitespace
        Assert.assertFalse(Tools.isValidEntityId("period_1period"));     // underscore not allowed
        Assert.assertFalse(Tools.isValidEntityId("e47ac10b<script1"));
        Assert.assertFalse(Tools.isValidEntityId("üuidshape-1234"));     // non-ASCII
    }

    @Test
    public void isValidEntityId_rejectsLeadingHyphen() {
        Assert.assertFalse(Tools.isValidEntityId("-47ac10b-58cc-4372"));
    }

    // === reserved GraphQL suffix words (generated-name namespace, 2026-08-29) ===

    @Test
    public void isSpecCompliant_rejectsReservedSuffixWords() {
        Assert.assertFalse(Tools.isSpecCompliant("fooClassification"));
        Assert.assertFalse(Tools.isSpecCompliant("fooWhere"));
        Assert.assertFalse(Tools.isSpecCompliant("fooEnum"));
        Assert.assertFalse(Tools.isSpecCompliant("fooRapla"));
        Assert.assertFalse(Tools.isSpecCompliant("Enum"));
    }

    @Test
    public void isSpecCompliant_suffixRuleIsCaseSensitiveAndSuffixOnly() {
        Assert.assertTrue(Tools.isSpecCompliant("fooenum"));
        Assert.assertTrue(Tools.isSpecCompliant("Enumeration"));
        Assert.assertTrue(Tools.isSpecCompliant("whereabouts"));
        Assert.assertTrue(Tools.isSpecCompliant("exportrapla"));
    }

    @Test
    public void toSpecKey_escapesReservedSuffix() {
        Assert.assertEquals("fooEnum_", Tools.toSpecKey("fooEnum", java.util.Collections.emptySet()));
        Assert.assertEquals("foo_Where_", Tools.toSpecKey("foo Where", java.util.Collections.emptySet()));
        Assert.assertTrue(Tools.isSpecCompliant(Tools.toSpecKey("fooRapla", java.util.Collections.emptySet())));
    }

    @Test
    public void reservedTypeAndAttributeKeys() {
        Assert.assertTrue(Tools.isReservedTypeKey("String"));
        Assert.assertTrue(Tools.isReservedTypeKey("Category"));
        Assert.assertFalse(Tools.isReservedTypeKey("raum"));
        Assert.assertTrue(Tools.isReservedAttributeKey("AND"));
        Assert.assertTrue(Tools.isReservedAttributeKey("typeKey"));
        Assert.assertFalse(Tools.isReservedAttributeKey("name"));
    }
}
