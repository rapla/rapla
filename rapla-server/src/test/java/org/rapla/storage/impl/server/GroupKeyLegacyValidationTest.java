package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Category;
import org.rapla.framework.RaplaException;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PRD 058 Phase 6 — group keys (the {@code user-groups} Category subtree) are
 * validated with the pre-058 <em>legacy</em> key rule (Unicode letters/digits +
 * {@code '_'} / {@code '-'}), NOT the strict GraphQL identifier spec, because
 * group keys never become GraphQL identifiers (groups surface only via the
 * hand-written static {@code type Group { key: String! }}; PRD 069 group inputs
 * use slash-separated key-paths). Every other category keeps the strict spec.
 *
 * <p>Tier-2 round-trip via {@code facade.store(...)}. {@code testdefault.xml}
 * ships {@code user-groups/my-group} (a hyphenated group key), which a strict
 * write-guard would freeze — that's the regression this pins.
 */
class GroupKeyLegacyValidationTest extends FacadeTestSupport
{
    private Category userGroup(String key) throws RaplaException
    {
        return facade.getSuperCategory().getCategory("user-groups").getCategory(key);
    }

    @Test
    void groupKeyWithHyphenRoundTrips() throws Exception
    {
        Category editable = facade.edit(userGroup("my-group"));
        editable.setKey("my-renamed-group"); // hyphen — legacy ok, strict would reject
        facade.store(editable);
        assertNotNull(userGroup("my-renamed-group"), "hyphenated group key must round-trip");
    }

    @Test
    void groupKeyWithUmlautRoundTrips() throws Exception
    {
        Category editable = facade.edit(userGroup("my-group"));
        editable.setKey("prüfer"); // umlaut — legacy (Character.isLetter) ok
        facade.store(editable);
        assertNotNull(userGroup("prüfer"), "umlaut group key must round-trip");
    }

    @Test
    void groupKeyWithSpaceStillRejected() throws Exception
    {
        Category editable = facade.edit(userGroup("my-group"));
        editable.setKey("with space");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }

    @Test
    void groupKeyWithSlashStillRejected() throws Exception
    {
        Category editable = facade.edit(userGroup("my-group"));
        editable.setKey("with/slash");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }

    @Test
    void nonGroupCategoryWithHyphenStillRejected() throws Exception
    {
        // A category outside the user-groups subtree keeps the strict GraphQL spec.
        Category editable = facade.edit(facade.getSuperCategory().getCategory("department"));
        editable.setKey("with-hyphen");
        assertThrows(RaplaException.class, () -> facade.store(editable));
    }
}
