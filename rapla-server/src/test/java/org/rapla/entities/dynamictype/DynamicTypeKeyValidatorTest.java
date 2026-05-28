package org.rapla.entities.dynamictype;

import org.junit.jupiter.api.Test;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 058 Phase 1 — verifies that {@code facade.store(...)} rejects DynamicType
 * and Attribute keys that violate the GraphQL identifier spec
 * ({@code [A-Za-z_][A-Za-z0-9_]*}). The validator hook lives in
 * {@code DynamicTypeImpl.checkKey} and now routes through
 * {@code Tools.isSpecCompliant}.
 *
 * <p>Tier-2 round-trip: edits a real DynamicType, sets a non-spec key, attempts
 * {@code facade.store(...)}, expects {@code RaplaException} with the
 * {@code error.invalid_key} i18n key.
 */
class DynamicTypeKeyValidatorTest extends FacadeTestSupport
{
    @Test
    void specCompliantKeyAccepted() throws Exception
    {
        DynamicType original = facade.getDynamicTypes(null)[0];
        DynamicType editable = facade.edit(original);
        editable.setKey("renamed_ok_123");
        facade.store(editable);
        // Refetch: the rename took.
        DynamicType refreshed = facade.getDynamicType("renamed_ok_123");
        assertEquals("renamed_ok_123", refreshed.getKey());
    }

    @Test
    void typeKeyWithUmlautRejected() throws Exception
    {
        DynamicType editable = facade.edit(facade.getDynamicTypes(null)[0]);
        editable.setKey("Prüfer");
        RaplaException ex = assertThrows(RaplaException.class, () -> facade.store(editable));
        assertTrue(ex.getMessage().toLowerCase().contains("prüfer")
                        || ex.getMessage().toLowerCase().contains("invalid"),
                "expected invalid_key-style message, got: " + ex.getMessage());
    }

    @Test
    void typeKeyWithLeadingDigitRejected() throws Exception
    {
        DynamicType editable = facade.edit(facade.getDynamicTypes(null)[0]);
        editable.setKey("1leading");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }

    @Test
    void typeKeyWithSpaceRejected() throws Exception
    {
        DynamicType editable = facade.edit(facade.getDynamicTypes(null)[0]);
        editable.setKey("with space");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }

    @Test
    void typeKeyWithHyphenRejected() throws Exception
    {
        DynamicType editable = facade.edit(facade.getDynamicTypes(null)[0]);
        editable.setKey("with-hyphen");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }

    @Test
    void typeKeyWithDotRejected() throws Exception
    {
        DynamicType editable = facade.edit(facade.getDynamicTypes(null)[0]);
        editable.setKey("with.dot");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }

    @Test
    void attributeKeyWithUmlautRejected() throws Exception
    {
        DynamicType editable = facade.edit(facade.getDynamicTypes(null)[0]);
        editable.getAttributes()[0].setKey("Größe");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }

    @Test
    void attributeKeyAsciiCleanAccepted() throws Exception
    {
        DynamicType editable = facade.edit(facade.getDynamicTypes(null)[0]);
        editable.getAttributes()[0].setKey("ascii_clean");
        facade.store(editable);
        DynamicType refreshed = facade.getDynamicType(editable.getKey());
        assertTrue(refreshed.getAttribute("ascii_clean") != null,
                "renamed attribute must round-trip");
    }

    @Test
    void categoryKeyWithUmlautRejectedAtDispatch() throws Exception
    {
        // Category keys are checked at the dispatch path (checkConsitency)
        // and at the new explicit checkGraphqlKeySpecCompliance step.
        org.rapla.entities.Category dept = facade.getSuperCategory().getCategory("department");
        org.rapla.entities.Category editable = facade.edit(dept);
        editable.setKey("Größe");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }
}
