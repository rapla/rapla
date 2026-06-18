package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 070 — the Exchange connector user dialog (Swing {@code UserOptionPanel})
 * calls {@code GET /api/exchange/connect} via {@code ExchangeConnectorRemote}.
 * The controller for that interface was never created during the PRD 049
 * migration, so the call 404s and the dialog shows "Could not load: 404".
 * This asserts the endpoint is mapped (returns 200, not 404 Whitelabel) for an
 * authenticated user with no Exchange connection (status = disconnected).
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ExchangeConnectorControllerTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @Test
    void getSynchronizationStatusIsMapped() throws Exception
    {
        String token = OAuthTestSupport.loginAs(mockMvc, "admin", "");
        mockMvc.perform(get("/api/exchange/connect")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
