package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class RemoteLoggerControllerTest
{
    @Autowired
    MockMvc mockMvc;

    @Test
    void putLoggerEndpointAccepts200() throws Exception
    {
        mockMvc.perform(put("/logger/some-client-id")
                        .contentType("text/plain")
                        .content("hello from client"))
                .andExpect(status().isOk());
    }
}
