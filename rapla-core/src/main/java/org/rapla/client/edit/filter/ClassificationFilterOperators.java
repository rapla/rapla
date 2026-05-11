package org.rapla.client.edit.filter;

import org.rapla.entities.dynamictype.AttributeType;

import java.util.List;

/**
 * Pure-Java catalog mapping {@link AttributeType} → valid operator strings
 * and {@code <type, index>} ↔ operator round-trip.
 * <p>
 * Carved out of {@code ClassifiableFilterEdit.RuleRow.getOperatorValue()}
 * and {@code setOperatorValue()}, where the same bidirectional table was
 * duplicated as two if/else ladders. Centralising avoids drift between
 * the two directions.
 * <p>
 * Operator strings here are the same wire-format tokens consumed by
 * {@link org.rapla.entities.dynamictype.ClassificationFilter#setRule}.
 * No Swing imports — the JComboBox index/value mapping in the Swing
 * layer is now derived from this catalog.
 */
public final class ClassificationFilterOperators
{
    private ClassificationFilterOperators() {}

    public static final String IS       = "is";
    public static final String CONTAINS = "contains";
    public static final String STARTS   = "starts";
    public static final String ENDS     = "ends";
    public static final String LT       = "<";
    public static final String EQ       = "=";
    public static final String GT       = ">";
    public static final String NE       = "<>";
    public static final String LE       = "<=";
    public static final String GE       = ">=";

    private static final List<String> STRING_OPS  = List.of(CONTAINS, STARTS, ENDS);
    private static final List<String> NUMERIC_OPS = List.of(LT, EQ, GT, NE, LE, GE);
    private static final List<String> IS_ONLY     = List.of(IS);

    /**
     * Operator strings for the given attribute type, in UI display order.
     * For ALLOCATABLE / CATEGORY / BOOLEAN this is a single-element list
     * ({@code "is"}) — no operator combobox is shown for those types.
     */
    public static List<String> operatorsFor(AttributeType type)
    {
        if (type == null) throw new IllegalArgumentException("type must not be null");
        return switch (type)
        {
            case STRING               -> STRING_OPS;
            case INT, DATE            -> NUMERIC_OPS;
            case ALLOCATABLE, CATEGORY, BOOLEAN -> IS_ONLY;
        };
    }

    /**
     * Default operator for an attribute type — the value to use when the
     * stored rule has no operator (or null). Mirrors the
     * {@code setOperatorValue(null)} branches.
     */
    public static String defaultOperatorFor(AttributeType type)
    {
        return operatorsFor(type).get(0);
    }

    /**
     * Operator string for the given combobox index. Indices match the
     * order returned by {@link #operatorsFor(AttributeType)}.
     */
    public static String operatorAt(AttributeType type, int index)
    {
        List<String> ops = operatorsFor(type);
        if (index < 0 || index >= ops.size())
        {
            throw new IndexOutOfBoundsException(
                    "operator index " + index + " out of range for " + type + " (0.." + (ops.size() - 1) + ")");
        }
        return ops.get(index);
    }

    /**
     * Combobox index for the given operator string under the given type.
     * Returns {@code 0} when the operator is unknown for that type — this
     * matches the legacy Swing behaviour where unrecognised operators
     * silently fell back to the first option.
     * <p>
     * Special case: for INT/DATE, the legacy code treated {@code "is"} as
     * synonymous with {@code "="}; that alias is preserved here.
     */
    public static int indexOf(AttributeType type, String operator)
    {
        if (operator == null)
        {
            return 0;
        }
        // Legacy alias: "is" on a numeric attribute reads as "=" (index 1).
        if ((type == AttributeType.INT || type == AttributeType.DATE) && operator.equals(IS))
        {
            return 1;
        }
        List<String> ops = operatorsFor(type);
        int idx = ops.indexOf(operator);
        return idx < 0 ? 0 : idx;
    }

    /**
     * True when the attribute type uses a JComboBox to pick the operator
     * (STRING / INT / DATE). The other types render as a label with the
     * single fixed operator {@code "is"}.
     */
    public static boolean hasOperatorChoice(AttributeType type)
    {
        return operatorsFor(type).size() > 1;
    }
}
