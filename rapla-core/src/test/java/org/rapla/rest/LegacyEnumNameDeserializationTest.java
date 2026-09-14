package org.rapla.rest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.dynamictype.AttributeType;

import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the Rapla mapper reads enums written in BOTH forms:
 *
 * <ul>
 *   <li>the canonical {@code toString()} form ({@code "string"}, {@code "weekly"}) —
 *       what Jackson 3 writes and what rapla's XML store uses;</li>
 *   <li>the legacy {@code name()} form ({@code "STRING"}, {@code "WEEKLY"}) — what the
 *       pre-PRD-011 Gson serializer (and Jackson 2) wrote, and what is still persisted
 *       in older HSQLDB / DB stores.</li>
 * </ul>
 *
 * <p>Jackson 3 flipped the enum default from {@code name()} to {@code toString()}; without
 * a tolerant deserializer the rapla mapper rejects every legacy {@code name()}-form value,
 * so loading a pre-Jackson-3 database fails on the first enum-typed attribute. The
 * {@code toString()}-then-{@code name()} fallback installed by {@link JacksonObjectMapperFactory}
 * heals that on read while serialization stays canonical {@code toString()}.
 */
@RunWith(JUnit4.class)
public class LegacyEnumNameDeserializationTest
{
    private final JsonMapper mapper = JacksonObjectMapperFactory.create();

    @Test
    public void readsCanonicalToStringForm()
    {
        Assert.assertEquals(AttributeType.STRING, mapper.readValue("\"string\"", AttributeType.class));
        Assert.assertEquals(AttributeType.CATEGORY, mapper.readValue("\"rapla:category\"", AttributeType.class));
        Assert.assertEquals(AttributeType.ALLOCATABLE, mapper.readValue("\"rapla:allocatable\"", AttributeType.class));
        Assert.assertEquals(RepeatingType.WEEKLY, mapper.readValue("\"weekly\"", RepeatingType.class));
        Assert.assertEquals(RequestStatus.REQUESTED, mapper.readValue("\"requested\"", RequestStatus.class));
    }

    @Test
    public void readsLegacyGsonNameForm()
    {
        Assert.assertEquals(AttributeType.STRING, mapper.readValue("\"STRING\"", AttributeType.class));
        Assert.assertEquals(AttributeType.CATEGORY, mapper.readValue("\"CATEGORY\"", AttributeType.class));
        Assert.assertEquals(AttributeType.ALLOCATABLE, mapper.readValue("\"ALLOCATABLE\"", AttributeType.class));
        Assert.assertEquals(RepeatingType.WEEKLY, mapper.readValue("\"WEEKLY\"", RepeatingType.class));
        Assert.assertEquals(RepeatingType.MONTHLY, mapper.readValue("\"MONTHLY\"", RepeatingType.class));
        Assert.assertEquals(RequestStatus.REQUESTED, mapper.readValue("\"REQUESTED\"", RequestStatus.class));
    }

    @Test
    public void serializationStaysCanonicalToStringForm()
    {
        Assert.assertEquals("\"string\"", mapper.writeValueAsString(AttributeType.STRING));
        Assert.assertEquals("\"rapla:category\"", mapper.writeValueAsString(AttributeType.CATEGORY));
        Assert.assertEquals("\"weekly\"", mapper.writeValueAsString(RepeatingType.WEEKLY));
    }

    @Test(expected = Exception.class)
    public void rejectsGenuinelyUnknownValue()
    {
        mapper.readValue("\"NOT_A_TYPE\"", AttributeType.class);
    }
}
