package org.rapla.server.spring.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.StorageOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * PRD 074 §"Window and inputs directives" — save-time validation of the {@code @param}/{@code
 * @window} contract. A typo'd {@code into} path must be rejected at {@code saveView}, not
 * discovered as a silently-empty document render.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ViewParamSaveValidationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ViewParamSaveValidationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @Autowired ViewCatalogService views;
    @Autowired StorageOperator operator;

    private static final String BODY = "{ reservations(filter: $filter) { titel: name } }";

    private List<String> save(String name, String query) throws Exception
    {
        User admin = operator.getUser("homer");
        return views.saveView(name, query, true, List.of(), null, admin);
    }

    @Test
    void aValidParamAndWindowSave() throws Exception
    {
        String query = """
                query psv_valid($filter: ReservationFilter!) @view(title: "ok")
                  @window(from: { anchor: TODAY, offset: 0 }, to: { anchor: TODAY, offset: 7 })
                  @param(name: "resource", into: "filter.allocatableIdsIn")
                """ + BODY;
        assertEquals(List.of(), save("psv_valid", query));
    }

    @Test
    void anIntoTargetingAWholeVariableIsValid() throws Exception
    {
        String query = """
                query psv_whole($eventId: ID!) @view(title: "ok")
                  @param(name: "eventId", into: "eventId", required: true)
                { reservation(id: $eventId) { titel: name } }""";
        assertEquals(List.of(), save("psv_whole", query));
    }

    /**
     * The error is the poor-man's autocomplete: an editor we cannot extend (GraphiQL is
     * CDN-loaded) still tells the author which fields exist. Monaco-side completion folds into
     * PRD 074 Phase 4 and reuses the same walk.
     */
    @Test
    void aTypodIntoFieldIsRejectedAndNamesTheAlternatives() throws Exception
    {
        String query = """
                query psv_typo($filter: ReservationFilter!) @view(title: "x")
                  @param(name: "resource", into: "filter.allocatableIdsInX")
                """ + BODY;
        List<String> errors = save("psv_typo", query);
        assertFalse(errors.isEmpty());
        String message = errors.get(0);
        assertTrue(message.contains("allocatableIdsInX"), message);
        assertTrue(message.contains("available:"), message);
        assertTrue(message.contains("allocatableIdsIn"), message);
        assertTrue(message.contains("ownerEq"), message);
    }

    @Test
    void anIntoOnAnUndeclaredVariableIsRejectedAndNamesTheDeclaredOnes() throws Exception
    {
        String query = """
                query psv_novar($filter: ReservationFilter!) @view(title: "x")
                  @param(name: "resource", into: "nosuchvar.allocatableIdsIn")
                """ + BODY;
        List<String> errors = save("psv_novar", query);
        assertFalse(errors.isEmpty());
        String message = errors.get(0);
        assertTrue(message.contains("nosuchvar"), message);
        assertTrue(message.contains("declared variables:"), message);
        assertTrue(message.contains("filter"), message);
    }

    @Test
    void duplicatePublicNamesAreRejected() throws Exception
    {
        String query = """
                query psv_dup($filter: ReservationFilter!) @view(title: "x")
                  @param(name: "resource", into: "filter.allocatableIdsIn")
                  @param(name: "resource", into: "filter.ownerEq")
                """ + BODY;
        List<String> errors = save("psv_dup", query);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("resource"), errors.toString());
    }

    @Test
    void aParamNamedFromCollidesWithADeclaredWindow() throws Exception
    {
        String query = """
                query psv_fromclash($filter: ReservationFilter!) @view(title: "x")
                  @window(from: { anchor: TODAY, offset: 0 }, to: { anchor: TODAY, offset: 7 })
                  @param(name: "from", into: "filter.ownerEq")
                """ + BODY;
        List<String> errors = save("psv_fromclash", query);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("from"), errors.toString());
    }

    @Test
    void aWindowOnAnUndeclaredVariableIsRejected() throws Exception
    {
        String query = """
                query psv_badwindow($filter: ReservationFilter!) @view(title: "x")
                  @window(into: "nosuchvar", from: { anchor: TODAY, offset: 0 }, to: { anchor: TODAY, offset: 7 })
                """ + BODY;
        List<String> errors = save("psv_badwindow", query);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("nosuchvar"), errors.toString());
    }

    @Test
    void aWindowOnAVariableWithoutFromToIsRejected() throws Exception
    {
        String query = """
                query psv_nofromto($eventId: ID!) @view(title: "x")
                  @window(into: "eventId", from: { anchor: TODAY, offset: 0 }, to: { anchor: TODAY, offset: 7 })
                { reservation(id: $eventId) { titel: name } }""";
        List<String> errors = save("psv_nofromto", query);
        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("eventId"), errors.toString());
    }

    /**
     * A dry-run check for the editor: same validation as {@code saveView}, but it stores nothing
     * and reports the offending <b>line/column</b>. Without a position nothing can be marked red —
     * which is exactly why the editor could only alert() before.
     */
    @Test
    void validateDryRunsWithoutStoringAndReportsThePosition()
    {
        String query = """
                query psv_dry($filter: ReservationFilter!) @view(title: "x")
                  @param(name: "resource", into: "filter.allocatableIdsInX")
                { reservations(filter: $filter) { titel: name } }""";

        List<ViewParamDirectives.Issue> issues = views.validateQuery(query);

        assertEquals(1, issues.size(), issues.toString());
        ViewParamDirectives.Issue issue = issues.get(0);
        assertEquals(2, issue.line(), "the @param directive is on line 2");
        assertTrue(issue.column() > 0, "column must be 1-based, was " + issue.column());
        assertTrue(issue.message().contains("available:"), issue.message());
        // dry run: nothing was stored
        assertTrue(views.findView("psv_dry").isEmpty(), "validate must not store the view");
    }

    @Test
    void validateReturnsNoIssuesForAGoodQuery()
    {
        String query = """
                query psv_dry_ok($filter: ReservationFilter!) @view(title: "ok")
                  @window(from: { anchor: WEEK_START, offset: 0 }, to: { anchor: WEEK_START, offset: 7 })
                  @param(name: "resource", into: "filter.allocatableIdsIn")
                { reservations(filter: $filter) { titel: name } }""";
        assertEquals(List.of(), views.validateQuery(query));
    }

    /** A plain GraphQL error (unknown field) must also carry a position, so it marks red too. */
    @Test
    void validateReportsPlainGraphQLErrorsWithAPositionToo()
    {
        String query = """
                query psv_dry_bad($filter: ReservationFilter!) @view(title: "x")
                { reservations(filter: $filter) { nosuchfield } }""";
        List<ViewParamDirectives.Issue> issues = views.validateQuery(query);
        assertFalse(issues.isEmpty());
        assertTrue(issues.get(0).line() > 0, "expected a line for a schema error");
    }
}
