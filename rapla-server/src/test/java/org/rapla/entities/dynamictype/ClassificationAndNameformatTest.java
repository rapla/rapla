package org.rapla.entities.dynamictype;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-2 backfill (PRD 017 Phase 4 — next round) for the
 * {@code Classification} → {@code DynamicType} → {@code ParsedText} chain.
 *
 * <p>This is the exact code path the PRD 011 (Spring Boot 4 / Jackson 3)
 * cutover broke (the "resource names empty in GUI" bug — see
 * {@link org.rapla.client.spring.HeadlessClientNameResolutionIntegrationTest}).
 * That test runs as a tier-4 e2e (~10 s + Spring boot); this one exercises
 * the same chain at tier-2 cost (~150 ms + facade boot) and surfaces
 * regressions much faster.
 *
 * <p>Targets:
 * <ul>
 *   <li>{@code ClassificationImpl.getName(locale)} — nameformat resolution</li>
 *   <li>{@code ClassificationImpl.getValue / getValueForAttribute / getValueAsString}</li>
 *   <li>{@code ClassificationImpl.setValue} → {@code getName} feedback loop</li>
 *   <li>{@code DynamicType.getAttributes / getAttribute(key)}</li>
 *   <li>{@code ParsedText} resolution against real attribute values</li>
 * </ul>
 */
class ClassificationAndNameformatTest extends FacadeTestSupport
{
    private static final Locale LOCALE = Locale.ENGLISH;

    // ---------- getName / nameformat ----------

    @Test
    void everyAllocatableHasNonEmptyName() throws Exception
    {
        // The PRD 011 bug shape: post-deserialize, getName returned "" or
        // the entity id instead of the resolved nameformat. This is the
        // tier-2 canary.
        Allocatable[] all = facade.getAllocatables();
        assertTrue(all.length > 0);

        for (Allocatable a : all)
        {
            String name = a.getName(LOCALE);
            assertNotNull(name, "name null for " + a.getId());
            assertFalse(name.isEmpty(), "name empty for " + a.getId() + " — wire-format / resolver bug");
            assertFalse(name.equals(a.getId()),
                    "name fell back to id for " + a.getId() + " — type/classification didn't resolve");
        }
    }

    @Test
    void getNameOnEditableCopyReflectsSetValue() throws Exception
    {
        // For a room allocatable (nameformat {name}), edit a writable copy
        // via the facade, mutate, and verify getName reflects the change.
        // This exercises the setValue → ParsedText feedback loop AND the
        // facade.editAsync clone path in one test.
        Allocatable target = findByTypeKey("room");
        assertNotNull(target, "fixture must include a room");

        Allocatable editable = waitFor(facade.editAsync(target));
        editable.getClassification().setValue("name", "ROOM-RENAMED");
        assertEquals("ROOM-RENAMED", editable.getName(LOCALE),
                "getName must reflect setValue on the editable copy");

        // Original (read-only view) is untouched — proves the editable copy
        // is detached and we're not mutating shared cache.
        assertFalse("ROOM-RENAMED".equals(target.getName(LOCALE)),
                "original (read-only) allocatable must not pick up the edit");
    }

    // ---------- getValue / getValueAsString ----------

    @Test
    void getValueAndGetValueForAttributeAgree() throws Exception
    {
        Allocatable target = findByTypeKey("room");
        assertNotNull(target);
        Classification c = target.getClassification();

        Object byKey = c.getValue("name");
        assertNotNull(byKey, "name attribute must have a value in fixture");

        Attribute nameAttr = c.getType().getAttribute("name");
        assertNotNull(nameAttr);
        Object byAttr = c.getValueForAttribute(nameAttr);
        assertEquals(byKey, byAttr,
                "getValue(key) and getValueForAttribute(attr) must agree on the same attribute");
    }

    @Test
    void getValueAsStringIsNonNullForPresentAttributes() throws Exception
    {
        // Per the contract, getValueAsString is the rendered form used in
        // tables / UI. After deserialize it must be non-null for attributes
        // with a value (the PRD 011 bug surface again, but on the per-cell
        // rendering path rather than the row name).
        Allocatable target = findByTypeKey("room");
        Classification c = target.getClassification();

        for (Attribute attr : c.getType().getAttributes())
        {
            Object v = c.getValueForAttribute(attr);
            if (v == null) continue; // optional attribute, skip
            String rendered = c.getValueAsString(attr, LOCALE);
            assertNotNull(rendered, "getValueAsString null for present attribute " + attr.getKey());
        }
    }

    // ---------- DynamicType / Attribute lookup ----------

    @Test
    void dynamicTypeAttributeLookupIsConsistent() throws Exception
    {
        for (DynamicType type : facade.getDynamicTypes(null))
        {
            Attribute[] attrs = type.getAttributes();
            for (Attribute attr : attrs)
            {
                Attribute byKey = type.getAttribute(attr.getKey());
                assertSame(attr, byKey,
                        "type.getAttribute(key) must return the SAME instance as iteration");
                assertNotNull(attr.getType(), "attribute type must not be null after deserialize");
            }
        }
    }

    @Test
    void allocatableClassificationTypeMatchesDeclaredType() throws Exception
    {
        // After deserialize, a.getClassification().getType() should resolve
        // to one of facade.getDynamicTypes(null). The resolver bug PRD 011
        // surfaced left getType() returning null in some cases.
        DynamicType[] allTypes = facade.getDynamicTypes(null);
        for (Allocatable a : facade.getAllocatables())
        {
            Classification c = a.getClassification();
            assertNotNull(c, "classification null for " + a.getId());
            DynamicType t = c.getType();
            assertNotNull(t, "type null on classification for " + a.getId() + " — resolver wiring broken");

            boolean inSet = false;
            for (DynamicType candidate : allTypes)
            {
                if (candidate.getId().equals(t.getId())) { inSet = true; break; }
            }
            assertTrue(inSet, "classification.type for " + a.getId() + " not in facade.getDynamicTypes()");
        }
    }

    // ---------- helpers ----------

    private Allocatable findByTypeKey(String typeKey) throws Exception
    {
        for (Allocatable a : facade.getAllocatables())
        {
            DynamicType t = a.getClassification().getType();
            if (t != null && typeKey.equals(t.getKey())) return a;
        }
        return null;
    }
}
