package org.rapla.server.spring;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.web.IsolatedDefaultDatasetTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** PRD 118 D8-8 — the banner text is configuration, never markup: the login page emits it HTML-escaped. */
@SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.demo.banner=<b onclick=\"x()\">Demo</b> & reset")
@AutoConfigureMockMvc
@Tag("e2e")
class DemoBannerEscapingTest extends IsolatedDefaultDatasetTest
{
    @Autowired MockMvc mockMvc;

    @Test
    void theLoginPageEscapesTheBanner() throws Exception
    {
        String page = mockMvc.perform(get("/login")).andReturn().getResponse().getContentAsString();
        assertTrue(page.contains("&lt;b onclick=&quot;x()&quot;&gt;Demo&lt;/b&gt; &amp; reset"), page);
        assertFalse(page.contains("<b onclick"), page);
    }
}
