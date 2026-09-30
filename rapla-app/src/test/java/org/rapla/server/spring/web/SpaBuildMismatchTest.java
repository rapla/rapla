package org.rapla.server.spring.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * PRD 125 Phase 2: the build id is the hashed main bundle name the served index.html
 * references. A request whose X-Rapla-Build differs gets X-Rapla-Build-Mismatch, and is
 * never rejected (D1).
 */
@Tag("e2e")
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class SpaBuildMismatchTest extends IsolatedDefaultDatasetTest
{
    private static final Pattern MAIN = Pattern.compile("main-[\\w-]+\\.js");

    @Autowired
    MockMvc mockMvc;

    String servedBuild;

    @BeforeEach
    void readServedBuild() throws Exception
    {
        String index = mockMvc.perform(get("/app/")).andReturn().getResponse().getContentAsString();
        Matcher m = MAIN.matcher(index);
        assertTrue(m.find(), "served index.html must reference a main-<hash>.js");
        servedBuild = m.group();
    }

    @Test
    void outdatedClientGetsMismatchHeader() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config").header("X-Rapla-Build", "main-OUTDATED.js"))
                .andExpect(header().string("X-Rapla-Build-Mismatch", servedBuild));
    }

    @Test
    void outdatedClientMutationIsNotRejected() throws Exception
    {
        int withoutHeader = mockMvc.perform(post("/api/auth/session/refresh"))
                .andReturn().getResponse().getStatus();
        int outdated = mockMvc.perform(post("/api/auth/session/refresh").header("X-Rapla-Build", "main-OUTDATED.js"))
                .andExpect(header().string("X-Rapla-Build-Mismatch", servedBuild))
                .andReturn().getResponse().getStatus();
        assertEquals(withoutHeader, outdated);
    }

    @Test
    void nonApiPathIsNotChecked() throws Exception
    {
        mockMvc.perform(get("/app/").header("X-Rapla-Build", "main-OUTDATED.js"))
                .andExpect(header().doesNotExist("X-Rapla-Build-Mismatch"));
    }

    @Test
    void currentClientGetsNoHeader() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config").header("X-Rapla-Build", servedBuild))
                .andExpect(header().doesNotExist("X-Rapla-Build-Mismatch"));
    }

    @Test
    void devClientIsNotChecked() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config").header("X-Rapla-Build", "dev"))
                .andExpect(header().doesNotExist("X-Rapla-Build-Mismatch"));
    }

    @Test
    void clientWithoutHeaderIsNotChecked() throws Exception
    {
        mockMvc.perform(get("/api/auth/oauth/config"))
                .andExpect(header().doesNotExist("X-Rapla-Build-Mismatch"));
    }
}
