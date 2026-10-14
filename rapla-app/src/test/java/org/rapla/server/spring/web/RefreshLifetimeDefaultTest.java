package org.rapla.server.spring.web;

import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** N unset: the default of 21 days (PRD 072 #7 cap, PRD 131 OQ1). */
@SpringBootTest(classes = RaplaSpringBootApplication.class)
@AutoConfigureMockMvc
class RefreshLifetimeDefaultTest extends AbstractRefreshLifetimeTest
{
    @Override
    int days()
    {
        return 21;
    }
}
