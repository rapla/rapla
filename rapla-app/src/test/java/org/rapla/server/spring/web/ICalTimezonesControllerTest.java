package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class ICalTimezonesControllerTest
{
    @Autowired
    MockMvc mockMvc;

    @Test
    void getTimezonesReturnsArray() throws Exception
    {
        mockMvc.perform(get("/api/ical/timezones"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void getDefaultTimezoneReturnsString() throws Exception
    {
        mockMvc.perform(get("/api/ical/timezones/default"))
                .andExpect(status().isOk());
    }
}
