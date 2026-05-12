package org.rapla.server.spring.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only fixture for {@link OpenApiSmokeTest#schemaUsesFieldBasedIntrospection_strictFixture}.
 *
 * <p>This POJO deliberately mixes patterns that <b>differ</b> between getter/setter
 * introspection (swagger-core's default) and field-based introspection (rapla's
 * {@code JacksonObjectMapperFactory} contract, which the {@code SwaggerJacksonConfig}
 * shim mirrors). The schema for this class is the ground truth: if any of the
 * assertions in the test fail, the SwaggerJacksonConfig wiring has regressed
 * — either the {@code @Bean ModelResolver} stopped being picked up
 * (springdoc #2574 lurking) or its visibility config drifted.
 *
 * <p>Expected schema (field-based discovery):
 * <ul>
 *   <li><b>{@code aField}</b> — included (private String field).</li>
 *   <li><b>{@code primitive}</b> — included (private boolean field).</li>
 *   <li><b>{@code ignoredTransient}</b> — EXCLUDED (Java {@code transient} marker +
 *       {@code PROPAGATE_TRANSIENT_MARKER}).</li>
 *   <li><b>{@code derivedFromGetter}</b> — EXCLUDED (has only a getter, no field;
 *       getters are invisible to swagger when GETTER=NONE).</li>
 *   <li><b>{@code phantomSetter}</b> — EXCLUDED (has only a setter, no field;
 *       setters are invisible when SETTER=NONE).</li>
 *   <li><b>{@code mismatchedField}</b> — type comes from the FIELD ({@code String}),
 *       NOT the getter ({@code Integer}). This is the swagger-core #1611 case —
 *       with getter-based discovery the schema would type it as integer despite
 *       the wire shipping a string.</li>
 * </ul>
 *
 * <p>Wire contract — what Jackson 3 actually serializes via
 * {@code JacksonObjectMapperFactory}: <b>{@code aField, primitive, mismatchedField}</b>.
 * The schema must match this set exactly.
 *
 * <p>The {@link FieldDiscoveryFixtureController} below exposes this type as a
 * response body so SpringDoc discovers it and emits its schema. The path
 * {@code /__test/swagger-fixture} is gated behind
 * {@code rapla.test.swagger-fixture-enabled=true}, which the test sets via
 * {@code @DynamicPropertySource}, so this endpoint never appears in production.
 */
public final class OpenApiFieldDiscoveryFixture
{
    private String aField = "from-field";
    private boolean primitive;
    @SuppressWarnings("unused")
    private transient String ignoredTransient = "shouldn't appear";

    // Field-vs-getter type conflict — schema must use the FIELD type (String).
    // The getter returning Integer is exactly the pattern swagger-core #1611
    // describes; with getter-based discovery, swagger types the property as
    // integer and the SPA's TypeScript client tries to parse a String as
    // Integer at runtime → silent NaN. Field-based discovery types it as
    // String, matching the wire.
    //
    // @JsonProperty rescues this field: the @JsonIgnore on getMismatchedField()
    // below tells Jackson "ignore this getter", which by Jackson 2's
    // any-ignorals-propagate rule would mark the entire `mismatchedField`
    // property as ignored — schema would lose it. @JsonProperty on the field
    // forces inclusion.
    @com.fasterxml.jackson.annotation.JsonProperty
    private String mismatchedField = "actually-a-string";

    public String getAField() { return aField; }
    public void setAField(String v) { this.aField = v; }

    public boolean isPrimitive() { return primitive; }
    public void setPrimitive(boolean v) { this.primitive = v; }

    /**
     * Returns a value but has no backing field. Under getter-based discovery
     * swagger would emit a {@code derivedFromGetter} property; under
     * field-based it must NOT appear.
     */
    public String getDerivedFromGetter() { return "synthesized"; }

    /**
     * Has no backing field. Under setter-based discovery swagger would emit
     * a {@code phantomSetter} property; under field-based it must NOT appear.
     */
    @SuppressWarnings("unused")
    public void setPhantomSetter(String ignored) { /* no-op */ }

    /**
     * Type mismatch — getter says {@code Integer}, field says {@code String}.
     * With {@code GETTER=NONE} the schema must derive the type from the field.
     * Annotated {@link JsonIgnore} as belt-and-suspenders so even if a future
     * swagger-core regression collects getters, it skips this one (otherwise
     * we'd hit a #1611-style type drift inside the test itself).
     */
    @JsonIgnore
    public Integer getMismatchedField() { return 42; }

    public void setMismatchedField(String v) { this.mismatchedField = v; }

    /**
     * Stub controller that exposes the fixture as a REST response body so
     * SpringDoc discovers it. Gated behind a test-only property — the
     * endpoint is invisible in production.
     */
    @RestController
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "rapla.test.swagger-fixture-enabled",
            havingValue = "true")
    public static class FieldDiscoveryFixtureController
    {
        @GetMapping(value = "/__test/swagger-fixture",
                produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
        public OpenApiFieldDiscoveryFixture get()
        {
            return new OpenApiFieldDiscoveryFixture();
        }
    }
}
