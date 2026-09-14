package org.rapla.storage.impl.server;

import org.junit.jupiter.api.Test;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.dynamictype.ClassificationFilter;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.entities.dynamictype.DynamicTypeAnnotations;
import org.rapla.test.util.FacadeTestSupport;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 086/087 Phase 4b — the flip flag must be <b>behaviour-preserving and instantly reversible</b>:
 * with the read-model flipped authoritative, {@code getAllocatables(filters)} must return exactly the
 * same set as the legacy scan (flag off). This is the differential that licenses the flip — if it ever
 * diverges, the bucket is wrong and the flip must not happen.
 */
class ReadModelFlipDifferentialTest extends FacadeTestSupport
{
    @Test
    void flippedGetAllocatables_equalsLegacyScan_perType() throws Exception
    {
        LocalAbstractCachableOperator op = (LocalAbstractCachableOperator) operator;
        DynamicType[] resourceTypes = facade.getDynamicTypes(DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE);
        assertTrue(resourceTypes.length > 0, "fixture must have a resource type");

        int comparedNonEmpty = 0;
        for (DynamicType type : resourceTypes)
        {
            ClassificationFilter[] filters = { type.newClassificationFilter() };

            op.setReadModelAuthoritative(false);   // legacy scan
            Set<String> legacy = ids(op.getAllocatables(filters));

            op.setReadModelAuthoritative(true);    // flipped: served from the bucket
            Set<String> flipped = ids(op.getAllocatables(filters));

            op.setReadModelAuthoritative(false);   // leave it as it was (reversible)

            assertEquals(legacy, flipped,
                    "flip changed the result for type " + type.getKey() + " — bucket diverges from the scan");
            if (!legacy.isEmpty()) comparedNonEmpty++;
        }
        assertTrue(comparedNonEmpty > 0, "no non-empty type compared — the flip was never exercised on real rows");
    }

    private static Set<String> ids(java.util.Collection<Allocatable> allocatables)
    {
        return allocatables.stream().map(Allocatable::getId).collect(Collectors.toCollection(TreeSet::new));
    }
}
