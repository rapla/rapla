package org.rapla.server.spring.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 125 Phase 1: content-hashed SPA bundles are cached forever, everything that
 * names them (index.html, SPA-route fallback, unhashed files) is revalidated.
 * Fixtures: {@code src/test/resources/static/app/}.
 */
@Tag("e2e")
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class SpaCacheHeadersTest extends IsolatedDefaultDatasetTest
{
    private static final String IMMUTABLE = "max-age=31536000, public, immutable";

    @Autowired
    MockMvc mockMvc;

    @Test
    void hashedBundleIsImmutable() throws Exception
    {
        mockMvc.perform(get("/app/main-TESTAB12.js"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", IMMUTABLE));
    }

    @Test
    void hashedMediaIsImmutable() throws Exception
    {
        mockMvc.perform(get("/app/media/icons-TEST_x-1.woff2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", IMMUTABLE));
    }

    @Test
    void indexHtmlIsRevalidated() throws Exception
    {
        mockMvc.perform(get("/app/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void spaRouteFallbackIsRevalidated() throws Exception
    {
        mockMvc.perform(get("/app/views/week"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache"));
    }

    @Test
    void missingHashedBundleIsNotAnsweredWithIndexHtml() throws Exception
    {
        // a stale chunk name after a deploy must 404, never index.html cached as immutable JS
        mockMvc.perform(get("/app/chunk-GONE0000.js"))
                .andExpect(status().isNotFound());
    }
}
