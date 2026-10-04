package org.rapla.entities.dynamictype;

import org.junit.jupiter.api.Test;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicTypeOrderTest
{
    private static DynamicTypeImpl type(String key, String classificationType, int createdDay, String order) throws Exception
    {
        LocalDateTime created = LocalDateTime.of(2020, 1, createdDay, 0, 0);
        DynamicTypeImpl dt = new DynamicTypeImpl(created, created);
        dt.setId(key);
        dt.setKey(key);
        dt.setAnnotation(DynamicTypeAnnotations.KEY_CLASSIFICATION_TYPE, classificationType);
        if (order != null)
        {
            dt.setAnnotation(DynamicTypeAnnotations.KEY_ORDER, order);
        }
        return dt;
    }

    private static List<String> sorted(DynamicType... types)
    {
        List<DynamicType> list = new ArrayList<>(List.of(types));
        list.sort(DynamicTypeImpl.TYPE_ORDER);
        return list.stream().map(DynamicType::getKey).collect(Collectors.toList());
    }

    private static final String RES = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_RESOURCE;
    private static final String PER = DynamicTypeAnnotations.VALUE_CLASSIFICATION_TYPE_PERSON;

    @Test
    void withoutOrderCreationOrderAndResourcesBeforePersons() throws Exception
    {
        assertEquals(List.of("room", "course", "lecturer"),
                sorted(type("lecturer", PER, 1, null), type("course", RES, 3, null), type("room", RES, 2, null)));
    }

    @Test
    void orderedTypesComeFirstAscendingNumerically() throws Exception
    {
        assertEquals(List.of("c", "b", "a", "d"),
                sorted(type("a", RES, 1, null), type("b", RES, 2, "20"), type("c", RES, 3, "9"), type("d", RES, 4, null)));
    }

    @Test
    void personTypeWithOrderStaysAfterResourceTypeWithout() throws Exception
    {
        assertEquals(List.of("room", "lecturer"), sorted(type("lecturer", PER, 1, "1"), type("room", RES, 2, null)));
    }

    @Test
    void nonNumericOrderCountsAsUnset() throws Exception
    {
        assertEquals(List.of("b", "a", "c"),
                sorted(type("a", RES, 1, "abc"), type("b", RES, 2, " 5 "), type("c", RES, 3, null)));
    }

    @Test
    void equalOrderFallsBackToCreationOrder() throws Exception
    {
        assertEquals(List.of("a", "b"), sorted(type("b", RES, 2, "10"), type("a", RES, 1, "10")));
    }
}
