package org.rapla.server.spring;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.rapla.server.spring.web.OAuthTestSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** PRD 118 D8-3 — without {@code demo} the allowlist is absent: a sample of the closed paths answers as before. */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Tag("e2e")
class DemoApiAllowlistDefaultTest extends IsolatedDefaultDatasetTest
{
    @Autowired MockMvc mockMvc;

    @Test
    void closedSampleStillAnswers() throws Exception
    {
        for (MockHttpServletRequestBuilder request : List.of(get("/api/jndi"), get("/raplaclient.jnlp"),
                post("/api/storage/change/email"), post("/api/auth/impersonate/switch"), get("/api/admin/permission-migration/findings")))
        {
            assertNotEquals(404, mockMvc.perform(request).andReturn().getResponse().getStatus(), request.toString());
        }
    }

    @Test
    void passwordGrantWorks() throws Exception
    {
        assertNotNull(OAuthTestSupport.loginAs(mockMvc, "admin", ""));
    }

    @Test
    void swingOAuthRedirectIsAccepted() throws Exception
    {
        org.springframework.mock.web.MockHttpSession session = (org.springframework.mock.web.MockHttpSession) mockMvc
                .perform(post("/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("username", "admin").param("password", ""))
                .andReturn().getRequest().getSession(false);
        assertNotNull(session);
        org.springframework.mock.web.MockHttpServletResponse response = mockMvc.perform(get("/oauth2/authorize?" + DemoApiAllowlistTest.SWING_AUTHORIZE_QUERY)
                .accept(MediaType.TEXT_HTML).session(session)).andReturn().getResponse();
        String location = String.valueOf(response.getHeader("Location"));
        assertEquals(302, response.getStatus(), location + " " + response.getContentAsString());
        assertTrue(location.startsWith("http://127.0.0.1:53111/login/oauth2/code/rapla") && location.contains("code="), location);
    }
}
