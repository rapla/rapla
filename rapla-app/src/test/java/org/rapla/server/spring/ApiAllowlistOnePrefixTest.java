package org.rapla.server.spring;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * PRD 118 D8-3a — outside the demo profile, {@code enabled} plus one open prefix opens exactly that subtree: every
 * other guarded path keeps the empty 404 of a missing path.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = { "rapla.api-allowlist.enabled=true", "rapla.api-allowlist.open-prefixes[0]=/api/recents",
        // review R1: a narrowed guard list is not configuration — /api must stay guarded
        "rapla.api-allowlist.guarded-prefixes[0]=/webclient" })
@Tag("e2e")
class ApiAllowlistOnePrefixTest extends IsolatedDefaultDatasetTest
{
    @Autowired MockMvc mockMvc;

    private MockHttpServletResponse get(String path) throws Exception
    {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)).andReturn().getResponse();
    }

    @Test
    void theAddedPrefixIsOpen() throws Exception
    {
        MockHttpServletResponse recents = get("/api/recents");
        assertNotEquals(404, recents.getStatus(), "the added prefix must pass the filter");
    }

    @Test
    void everythingElseGuardedStaysClosed() throws Exception
    {
        for (String path : List.of("/api/recentsx", "/api/favorites", "/api/graphql/schema", "/api/auth/me", "/api/documents",
                "/raplaclient.jnlp", "/webclient/rapla.jar"))
        {
            MockHttpServletResponse response = get(path);
            assertEquals(404, response.getStatus(), path);
            assertEquals("", response.getContentAsString(), path);
        }
    }
}
