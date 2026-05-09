package org.rapla.rest;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.dynamictype.internal.ParsedText;

import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.Field;

/**
 * Verifies PRD 011 Risk #4: that the Jackson 3 mapper produced by
 * {@link JacksonObjectMapperFactory} preserves transient-field initializers
 * during deserialization.
 *
 * <p>Why this matters: Rapla entities have multiple {@code transient} fields
 * with non-null initializers — e.g. {@code DynamicTypeImpl.parseContext = new
 * DynamicTypeParseContext(this)} and {@code ParsedText.first = ""}. The legacy
 * Jackson 2 / Gson stacks instantiated objects via the no-arg constructor, so
 * those initializers ran. If Jackson 3 instead instantiates via
 * {@code Unsafe.allocateInstance()} (skipping the constructor and field
 * initializers), those fields land as {@code null} after deserialization. That
 * would silently break {@code DynamicTypeImpl.setResolver(...) → annotation.init(parseContext)}
 * (NPE on null context) and {@code ParsedText.formatName(...)} (returns null
 * instead of "" for resources without a {@code {variable}} format).
 *
 * <p>The user observed empty resource names in the Swing client immediately
 * after the PRD 011 (Spring Boot 4 + Jackson 3) cutover. This test isolates
 * whether the transient-initializer behavior is the cause.
 */
@RunWith(JUnit4.class)
public class Jackson3TransientInitializerTest
{
    /**
     * Minimal probe: a POJO with a {@code transient String x = "hello"} initializer.
     * If Jackson 3 calls the no-arg constructor on deserialize, {@code x == "hello"}.
     * If Jackson 3 uses {@code Unsafe.allocateInstance}, {@code x == null}.
     */
    public static class TransientInitProbe
    {
        public transient String x = "hello";
        public String y;

        public TransientInitProbe() {}
    }

    @Test
    public void transientFieldInitializerSurvivesRoundTrip() throws Exception
    {
        JsonMapper mapper = JacksonObjectMapperFactory.create();
        TransientInitProbe original = new TransientInitProbe();
        original.y = "persisted";

        String json = mapper.writeValueAsString(original);
        TransientInitProbe restored = mapper.readValue(json, TransientInitProbe.class);

        Assert.assertEquals("persisted field y must round-trip", "persisted", restored.y);
        Assert.assertEquals(
            "transient field x with initializer \"hello\" must be re-initialized after deserialize "
            + "(if null, Jackson 3 is bypassing the no-arg constructor — confirms PRD 011 risk #4)",
            "hello", restored.x);
    }

    /**
     * The actual symptom: {@code ParsedText.first} defaults to {@code ""} via the
     * field initializer at line 48. After Jackson 3 deserialize it must NOT be null
     * — {@code formatName(ctx)} returns {@code first} as the resource name when the
     * format string has no {@code {variable}} parts.
     */
    @Test
    public void parsedTextFirstFieldHasInitializedValueAfterRoundTrip() throws Exception
    {
        JsonMapper mapper = JacksonObjectMapperFactory.create();
        ParsedText original = new ParsedText("just a static name");

        String json = mapper.writeValueAsString(original);
        ParsedText restored = mapper.readValue(json, ParsedText.class);

        Field firstField = ParsedText.class.getDeclaredField("first");
        firstField.setAccessible(true);
        Object firstValue = firstField.get(restored);

        Assert.assertNotNull(
            "ParsedText.first must not be null after Jackson 3 deserialize "
            + "(the field initializer `transient private String first = \"\"` must run)",
            firstValue);
        Assert.assertEquals("", firstValue);
    }

    /**
     * {@code DynamicTypeImpl.parseContext} is the parser the {@code setResolver(...)}
     * → {@code annotation.init(parseContext)} chain depends on. If null after
     * deserialize, the init either NPEs (for {variable} format strings) or appears
     * to succeed but leaves entity name resolution broken.
     */
    @Test
    public void dynamicTypeParseContextIsNonNullAfterRoundTrip() throws Exception
    {
        JsonMapper mapper = JacksonObjectMapperFactory.create();
        DynamicTypeImpl original = new DynamicTypeImpl();

        String json = mapper.writeValueAsString(original);
        DynamicTypeImpl restored = mapper.readValue(json, DynamicTypeImpl.class);

        Assert.assertNotNull(
            "DynamicTypeImpl.parseContext must not be null after Jackson 3 deserialize "
            + "(the field initializer `transient DynamicTypeParseContext parseContext = "
            + "new DynamicTypeParseContext(this)` must run, otherwise setResolver→init NPEs)",
            restored.getParseContext());
    }

    /**
     * Realistic shape: a {@code DynamicType} carrying a {@code nameformat} annotation
     * (the actual mechanism by which a resource computes its display name) must
     * survive a Jackson 3 round-trip with the annotation map intact.
     *
     * <p>If the annotations map is empty on the restored object, the resource name
     * will resolve to "" downstream regardless of any setResolver/init repair.
     * That would point the bug at the wire format itself (field naming, map key
     * handling, polymorphic typing of {@code ParsedText}) rather than the post-
     * deserialize init chain.
     */
    @Test
    public void dynamicTypeNameformatAnnotationSurvivesRoundTrip() throws Exception
    {
        JsonMapper mapper = JacksonObjectMapperFactory.create();
        DynamicTypeImpl original = new DynamicTypeImpl();
        original.setKey("room");
        // Bypass setAnnotation() here — it requires a live parseContext set up via
        // setResolver(StorageOperator) which we don't have in this isolated test.
        // Inject a ParsedText directly so we test purely the wire format.
        Field annotationsField = DynamicTypeImpl.class.getDeclaredField("annotations");
        annotationsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<String, ParsedText> annotations =
            (java.util.Map<String, ParsedText>) annotationsField.get(original);
        annotations.put("nameformat", new ParsedText("Room"));

        String json = mapper.writeValueAsString(original);
        System.out.println("Serialized DynamicType JSON: " + json);
        DynamicTypeImpl restored = mapper.readValue(json, DynamicTypeImpl.class);

        @SuppressWarnings("unchecked")
        java.util.Map<String, ParsedText> restoredAnnotations =
            (java.util.Map<String, ParsedText>) annotationsField.get(restored);

        Assert.assertNotNull("annotations map must not be null after deserialize", restoredAnnotations);
        Assert.assertTrue(
            "annotations map must contain 'nameformat' key after round-trip; was: " + restoredAnnotations.keySet(),
            restoredAnnotations.containsKey("nameformat"));

        ParsedText restoredNameformat = restoredAnnotations.get("nameformat");
        Assert.assertNotNull("nameformat ParsedText must not be null after deserialize", restoredNameformat);

        Field formatStringField = ParsedText.class.getDeclaredField("formatString");
        formatStringField.setAccessible(true);
        Assert.assertEquals(
            "nameformat ParsedText.formatString must round-trip its content",
            "Room", formatStringField.get(restoredNameformat));
    }

    /**
     * End-to-end probe: simulates the production setResolver→init→formatName chain
     * on a freshly deserialized {@link DynamicTypeImpl} carrying a plain-text
     * nameformat. {@code formatName} for a no-variable format returns the
     * {@code first} field, which is what the GUI displays as the resource name.
     *
     * <p>If this returns {@code null} or empty, the bug is somewhere in the
     * deserialize→init chain. If it returns "Plain Room Name", the bug is
     * elsewhere — e.g. in how {@code RemoteOperator.testResolveInitial} +
     * {@code AbstractCachableOperator.setResolver(Collection)} are invoked, or
     * in the actual server-side wire-format content the client receives.
     */
    @Test
    public void plainTextFormatNameSurvivesDeserializeAndInit() throws Exception
    {
        JsonMapper mapper = JacksonObjectMapperFactory.create();

        ParsedText original = new ParsedText("Plain Room Name");
        String json = mapper.writeValueAsString(original);
        ParsedText restored = mapper.readValue(json, ParsedText.class);

        // Simulate the setResolver→init chain. parseContext can be null for
        // no-variable formats because parseFunctions() is never called.
        restored.init(null);

        String formatted = restored.formatName(null);
        Assert.assertEquals(
            "After deserialize + init, formatName must return the literal text "
            + "(empty/null here would point to the deserialize→init chain as the bug)",
            "Plain Room Name", formatted);
    }
}
