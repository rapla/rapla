package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H2: repeated bad form-logins from the same IP+user get throttled (429) after the
 * free attempts are used up.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class LoginRateLimitTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @Test
    void repeatedBadLoginsEventuallyGet429() throws Exception
    {
        // 4 failures: first 3 free, the 4th arms the backoff window
        for (int i = 0; i < 4; i++)
        {
            mockMvc.perform(post("/login").param("username", "attacker").param("password", "wrong"));
        }
        // the 5th attempt falls inside the (1s) backoff window
        mockMvc.perform(post("/login").param("username", "attacker").param("password", "wrong"))
                .andExpect(status().is(429))
                .andExpect(header().exists("Retry-After"));
    }
}
