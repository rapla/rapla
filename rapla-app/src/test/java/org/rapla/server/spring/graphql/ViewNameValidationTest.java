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
 * A view's name is not a label — it is a <b>key</b>, in three places at once:
 *
 * <ul>
 *   <li>the <b>GraphQL operation name</b>: {@link StoredViewInterceptor} looks the view up by the
 *       request's {@code operationName} and swaps in the stored query <i>without</i> resetting it,
 *       so a name that differs from the stored query's operation name executes as
 *       "Unknown operation named …" — the view is silently broken,</li>
 *   <li>the {@link org.rapla.entities.storage.StoredArtifact} natural key ({@code kind + ":" + name}),</li>
 *   <li>a URL path segment (documents render at {@code /api/documents/{name}}).</li>
 * </ul>
 *
 * So the name must be a plain GraphQL identifier: {@code [_A-Za-z][_0-9A-Za-z]*} — no umlauts, no
 * spaces, no colon (which would split the artifact key), no leading digit. GraphQL's own grammar
 * has no unicode, so {@code query Übersicht} does not even parse: without this check an author can
 * save "Übersicht" against a {@code query Uebersicht} body and only find out at render time.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc(addFilters = false)
class ViewNameValidationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;

    @BeforeAll
    static void copyFixture() throws IOException
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = ViewNameValidationTest.class.getResourceAsStream("/testdefault.xml"))
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

    /** A query whose operation is named {@code op}. */
    private static String queryNamed(String op)
    {
        return "query " + op + " @view(title: \"x\") { serverTime }";
    }

    private List<String> save(String name, String query) throws Exception
    {
        User admin = operator.getUser("homer");
        return views.saveView(name, query, true, List.of(), null, admin);
    }

    @Test
    void aPlainIdentifierSaves() throws Exception
    {
        assertEquals(List.of(), save("vn_Uebersicht", queryNamed("vn_Uebersicht")));
    }

    @Test
    void anUmlautNameIsRejected() throws Exception
    {
        // The body must still parse, so the operation keeps an ASCII name — this is exactly the
        // trap: the pair looks plausible and would save today, then fail at render.
        List<String> errors = save("Übersicht", queryNamed("Uebersicht"));
        assertFalse(errors.isEmpty(), "umlaut name must be rejected");
        assertTrue(errors.toString().contains("Übersicht"), errors.toString());
    }

    @Test
    void aNameWithASpaceIsRejected() throws Exception
    {
        assertFalse(save("my view", queryNamed("myview")).isEmpty());
    }

    @Test
    void aNameWithAColonIsRejected() throws Exception
    {
        // ':' would split the artifact natural key (kind + ":" + name).
        assertFalse(save("view:1", queryNamed("view1")).isEmpty());
    }

    @Test
    void aNameStartingWithADigitIsRejected() throws Exception
    {
        assertFalse(save("1view", queryNamed("view1")).isEmpty());
    }

    @Test
    void aNameThatDiffersFromTheOperationNameIsRejected() throws Exception
    {
        // Both are valid identifiers, but the interceptor executes the stored query with the
        // requested operationName (= the view name) → "Unknown operation named 'vn_alpha'".
        List<String> errors = save("vn_alpha", queryNamed("vn_beta"));
        assertFalse(errors.isEmpty(), "name/operation mismatch must be rejected");
        assertTrue(errors.toString().contains("vn_beta"), errors.toString());
    }
}
