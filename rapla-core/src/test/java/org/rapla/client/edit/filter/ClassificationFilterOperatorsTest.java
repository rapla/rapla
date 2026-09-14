package org.rapla.client.edit.filter;

import org.junit.jupiter.api.Test;
import org.rapla.entities.dynamictype.AttributeType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tier-1 coverage of the operator catalog extracted from
 * {@code ClassifiableFilterEdit.RuleRow.{getOperatorValue,setOperatorValue}}.
 * Pins the bidirectional mapping so the next refactor cannot silently
 * drift one direction relative to the other.
 */
class ClassificationFilterOperatorsTest
{
    @Test
    void stringOperatorsContainsStartsEnds()
    {
        assertEquals(List.of("contains", "starts", "ends"),
                ClassificationFilterOperators.operatorsFor(AttributeType.STRING));
    }

    @Test
    void numericOperatorsForIntAndDate()
    {
        List<String> expected = List.of("<", "=", ">", "<>", "<=", ">=");
        assertEquals(expected, ClassificationFilterOperators.operatorsFor(AttributeType.INT));
        assertEquals(expected, ClassificationFilterOperators.operatorsFor(AttributeType.DATE));
    }

    @Test
    void singletonOperatorsForAllocatableCategoryBoolean()
    {
        assertEquals(List.of("is"), ClassificationFilterOperators.operatorsFor(AttributeType.ALLOCATABLE));
        assertEquals(List.of("is"), ClassificationFilterOperators.operatorsFor(AttributeType.CATEGORY));
        assertEquals(List.of("is"), ClassificationFilterOperators.operatorsFor(AttributeType.BOOLEAN));
    }

    @Test
    void defaultOperatorPerType()
    {
        assertEquals("contains", ClassificationFilterOperators.defaultOperatorFor(AttributeType.STRING));
        assertEquals("<",        ClassificationFilterOperators.defaultOperatorFor(AttributeType.INT));
        assertEquals("<",        ClassificationFilterOperators.defaultOperatorFor(AttributeType.DATE));
        assertEquals("is",       ClassificationFilterOperators.defaultOperatorFor(AttributeType.ALLOCATABLE));
        assertEquals("is",       ClassificationFilterOperators.defaultOperatorFor(AttributeType.CATEGORY));
        assertEquals("is",       ClassificationFilterOperators.defaultOperatorFor(AttributeType.BOOLEAN));
    }

    @Test
    void hasOperatorChoiceForVariableTypes()
    {
        assertTrue(ClassificationFilterOperators.hasOperatorChoice(AttributeType.STRING));
        assertTrue(ClassificationFilterOperators.hasOperatorChoice(AttributeType.INT));
        assertTrue(ClassificationFilterOperators.hasOperatorChoice(AttributeType.DATE));
        assertFalse(ClassificationFilterOperators.hasOperatorChoice(AttributeType.ALLOCATABLE));
        assertFalse(ClassificationFilterOperators.hasOperatorChoice(AttributeType.CATEGORY));
        assertFalse(ClassificationFilterOperators.hasOperatorChoice(AttributeType.BOOLEAN));
    }

    @Test
    void operatorAtIndexRoundTripsForString()
    {
        for (int i = 0; i < 3; i++)
        {
            String op = ClassificationFilterOperators.operatorAt(AttributeType.STRING, i);
            assertEquals(i, ClassificationFilterOperators.indexOf(AttributeType.STRING, op));
        }
    }

    @Test
    void operatorAtIndexRoundTripsForNumeric()
    {
        for (AttributeType t : new AttributeType[]{AttributeType.INT, AttributeType.DATE})
        {
            for (int i = 0; i < 6; i++)
            {
                String op = ClassificationFilterOperators.operatorAt(t, i);
                assertEquals(i, ClassificationFilterOperators.indexOf(t, op),
                        "round-trip failed for " + t + " index=" + i);
            }
        }
    }

    @Test
    void operatorAtRejectsOutOfBounds()
    {
        assertThrows(IndexOutOfBoundsException.class,
                () -> ClassificationFilterOperators.operatorAt(AttributeType.STRING, 3));
        assertThrows(IndexOutOfBoundsException.class,
                () -> ClassificationFilterOperators.operatorAt(AttributeType.INT, 6));
        assertThrows(IndexOutOfBoundsException.class,
                () -> ClassificationFilterOperators.operatorAt(AttributeType.DATE, -1));
    }

    @Test
    void indexOfUnknownOperatorReturnsZero()
    {
        // Legacy Swing behaviour: silently fall back to index 0.
        assertEquals(0, ClassificationFilterOperators.indexOf(AttributeType.STRING, "matches"));
        assertEquals(0, ClassificationFilterOperators.indexOf(AttributeType.INT, "between"));
    }

    @Test
    void indexOfNullOperatorReturnsZero()
    {
        assertEquals(0, ClassificationFilterOperators.indexOf(AttributeType.STRING, null));
        assertEquals(0, ClassificationFilterOperators.indexOf(AttributeType.INT, null));
        assertEquals(0, ClassificationFilterOperators.indexOf(AttributeType.ALLOCATABLE, null));
    }

    @Test
    void legacyIsAliasResolvesToEqualsOnNumericTypes()
    {
        // setOperatorValue treated "is" as "=" on INT/DATE — preserve.
        assertEquals(1, ClassificationFilterOperators.indexOf(AttributeType.INT, "is"));
        assertEquals(1, ClassificationFilterOperators.indexOf(AttributeType.DATE, "is"));
    }

    @Test
    void legacyIsAliasDoesNotApplyToStringType()
    {
        // "is" on STRING is not in the catalog and is not aliased — falls back to 0.
        assertEquals(0, ClassificationFilterOperators.indexOf(AttributeType.STRING, "is"));
    }

    @Test
    void operatorsForRejectsNull()
    {
        assertThrows(IllegalArgumentException.class,
                () -> ClassificationFilterOperators.operatorsFor(null));
    }
}
