package org.rapla.plugin.externaleventimport;

import org.junit.jupiter.api.Test;
import org.rapla.entities.dynamictype.AttributeType;
import org.rapla.entities.dynamictype.internal.AttributeImpl;
import org.rapla.entities.dynamictype.internal.ClassificationImpl;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.rest.JacksonObjectMapperFactory;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pins the PRD 068 sync wire shape: a {@link ClassificationImpl} riding as a plain DTO field
 * (concrete impl type, the {@code UpdateEvent} convention) must round-trip through the shared
 * rapla mapper without any abstract-type mapping — typeId/type-key/data survive so the
 * receiving side can {@code setResolver(...)} and read values.
 */
class SyncClassificationWireTest
{
    private final JsonMapper mapper = JacksonObjectMapperFactory.create();

    private static ClassificationImpl newClassification()
    {
        DynamicTypeImpl type = new DynamicTypeImpl();
        type.setKey("lecture");
        type.setId("dt-1");
        AttributeImpl name = new AttributeImpl(AttributeType.STRING);
        name.setKey("name");
        name.setId("att-1");
        type.addAttribute(name);
        ClassificationImpl cls = (ClassificationImpl) type.newClassificationWithoutCheck(false);
        cls.setValueForAttribute(name, "Analysis I");
        return cls;
    }

    @Test
    void requestRoundTripsClassificationAndSourceData()
    {
        SyncClassificationRequest original = new SyncClassificationRequest();
        ImportItem item = new ImportItem();
        item.setSourceItemId("v:42");
        item.setSourceData(Map.of("typeKey", "lecture", "kursIds", List.of("k1")));
        original.setSelectedItem(item);
        original.setClassification(newClassification());

        String json = mapper.writeValueAsString(original);
        SyncClassificationRequest restored = mapper.readValue(json, SyncClassificationRequest.class);

        assertNotNull(restored.getClassification(), "classification must survive the wire");
        assertEquals(original.getClassification(), restored.getClassification(),
                "ClassificationImpl.equals compares typeId/type/data — the full wire payload");
        assertEquals("v:42", restored.getSelectedItem().getSourceItemId());
        assertEquals("lecture", restored.getSelectedItem().getSourceData().get("typeKey"));
    }

    @Test
    void resultRoundTripsClassificationAndAllocatableIds()
    {
        SyncClassificationResult original = new SyncClassificationResult();
        original.setClassification(newClassification());
        original.setAllocatableIds(List.of("alloc-1", "alloc-2"));

        String json = mapper.writeValueAsString(original);
        SyncClassificationResult restored = mapper.readValue(json, SyncClassificationResult.class);

        assertEquals(original.getClassification(), restored.getClassification());
        assertEquals(List.of("alloc-1", "alloc-2"), restored.getAllocatableIds());
    }
}
