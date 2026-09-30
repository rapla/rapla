package org.rapla.server.spring.web;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.rapla.storage.CachableStorageOperator;
import org.rapla.storage.impl.server.LocalAbstractCachableOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.TimeZone;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bootstrap event a remote client receives after login ({@code GET /api/storage/resources})
 * must carry the server's timezone offset like every refresh event does. Without it
 * {@code RemoteOperator.updateTimestamps} stores 0 and {@code today()} is UTC-based until the
 * first refresh arrives — then it jumps by a day (visible between 22:00 and 24:00 UTC in CEST).
 */
@Tag("e2e")
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class BootstrapTimezoneOffsetIntegrationTest extends IsolatedDefaultDatasetTest
{
    @Autowired
    MockMvc mockMvc;

    @Autowired
    CachableStorageOperator operator;

    @Test
    void bootstrapEventCarriesTheServerTimezoneOffset() throws Exception
    {
        LocalAbstractCachableOperator local = (LocalAbstractCachableOperator) operator;
        TimeZone before = local.getTimeZone();
        // A fixed-offset zone: the expected value does not depend on DST or the wall clock.
        local.setTimeZone(TimeZone.getTimeZone("GMT+5"));
        try
        {
            String token = OAuthTestSupport.loginAs(mockMvc, "admin", "");
            mockMvc.perform(get("/api/storage/resources").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.timezoneOffset").value(5 * 60 * 60 * 1000));
        }
        finally
        {
            local.setTimeZone(before);
        }
    }
}
