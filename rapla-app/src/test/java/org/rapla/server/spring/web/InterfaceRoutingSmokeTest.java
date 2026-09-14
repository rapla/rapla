package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PRD 049 Phase 0 — does Spring 6 route from {@code @HttpExchange} declared
 * on the controller's implemented interface? Decides the rest of the PRD:
 * green = collapse to one annotation site per endpoint; red = keep controller
 * annotations + architecture-test path equality.
 */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
@Import(InterfaceRoutingSmokeTest.SmokeTestController.class)
class InterfaceRoutingSmokeTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @HttpExchange("/api/_smoketest")
    interface SmokeService
    {
        @GetExchange("/ping")
        String ping();

        @GetExchange("/echo/{name}")
        String echo(@PathVariable("name") String name);

        @GetExchange("/greet")
        String greet(@RequestParam("name") String name);

        @PostExchange("/upper")
        String upper(@RequestBody String body);
    }

    @RestController
    static class SmokeTestController implements SmokeService
    {
        @Override
        public String ping() { return "pong"; }

        @Override
        public String echo(String name) { return name; }

        @Override
        public String greet(String name) { return "hello " + name; }

        @Override
        public String upper(String body) { return body.toUpperCase(); }
    }

    private String token() throws Exception
    {
        return OAuthTestSupport.loginAs(mockMvc, "admin", "");
    }

    @Test
    void getExchangeRoutes() throws Exception
    {
        mockMvc.perform(get("/api/_smoketest/ping")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().string("pong"));
    }

    @Test
    void pathVariableInheritsFromInterface() throws Exception
    {
        mockMvc.perform(get("/api/_smoketest/echo/world")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().string("world"));
    }

    @Test
    void requestParamInheritsFromInterface() throws Exception
    {
        mockMvc.perform(get("/api/_smoketest/greet")
                        .param("name", "rapla")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().string("hello rapla"));
    }

    @Test
    void postExchangeWithRequestBodyInheritsFromInterface() throws Exception
    {
        mockMvc.perform(post("/api/_smoketest/upper")
                        .contentType("text/plain")
                        .content("loud")
                        .header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(content().string("LOUD"));
    }
}
