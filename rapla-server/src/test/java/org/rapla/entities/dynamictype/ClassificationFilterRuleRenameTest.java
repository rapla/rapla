package org.rapla.entities.dynamictype;

import org.junit.jupiter.api.Test;
import org.rapla.entities.Entity;
import org.rapla.test.util.FacadeTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tier-2 round-trip pinning the fix in
 * {@code ClassificationFilterRuleImpl.getAttribute()} — id-based resolution
 * must win when both {@code attributeId} and {@code attributeKey} are set,
 * with key as the legacy fallback. Without the fix, a saved filter rule
 * referencing a renamed attribute resolved to {@code null}, which made
 * {@code ClassificationFilterImpl.commitChange} silently drop the rule.
 *
 * <p>Discovered while wiring PRD 058 — the spec migration renames attribute
 * keys, and any filter rule referencing the renamed attribute would have been
 * removed during commitChange propagation. The fix is in rapla-core; this
 * test lives in rapla-server because it needs a writable DT through the
 * facade edit path.
 */
class ClassificationFilterRuleRenameTest extends FacadeTestSupport
{
    @Test
    void commitChangePropagatesRenameToFilterRule() throws Exception
    {
        // Build a filter against the 'name' attribute, then rename via setKey
        // on a fresh edit. Feed the (in-memory, stale) filter to the renamed
        // DT's commitChange path and verify the rule is *not* removed and
        // its stored attributeKey is now the new key.
        DynamicType room = facade.getDynamicType("room");
        Attribute originalName = room.getAttribute("name");
        ClassificationFilter filter = room.newClassificationFilter();
        filter.setRule(0, originalName, new Object[][] { { "contains", "A" } });
        int initialRuleCount = filter.ruleIterator().hasNext() ? 1 : 0;
        assertEquals(1, initialRuleCount);

        // Rename name → name_renamed and persist.
        DynamicType editable = (DynamicType) operator.editObject((Entity) room, null);
        editable.getAttribute("name").setKey("name_renamed");
        operator.storeAndRemove(java.util.Collections.singletonList((Entity) editable),
                java.util.Collections.emptyList(), null);

        // Apply commitChange directly — this is the code path
        // ClassificationFilterImpl.commitChange runs for every saved filter
        // referenced by addChangedDynamicTypeDependant during dispatch.
        DynamicType renamedRoom = facade.getDynamicType("room");
        ((org.rapla.entities.dynamictype.internal.ClassificationFilterImpl) filter)
                .commitChange(renamedRoom);

        // Rule must still exist and now point at the renamed attribute key.
        ClassificationFilterRule survivingRule = filter.ruleIterator().next();
        assertNotNull(survivingRule, "rule must not be dropped by commitChange");
        Attribute resolved = survivingRule.getAttribute();
        assertNotNull(resolved, "rule's attribute must resolve after commitChange");
        assertEquals("name_renamed", resolved.getKey(),
                "commitChange must update the rule's attributeKey to the renamed value");
    }
}
