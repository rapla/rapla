package org.rapla.server.spring.web;

import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** A deployment-configured N is honoured everywhere the default would be. */
@SpringBootTest(classes = RaplaSpringBootApplication.class, properties = "rapla.auth.remember-me-days=7")
@AutoConfigureMockMvc
class RefreshLifetimeConfiguredTest extends AbstractRefreshLifetimeTest
{
    @Override
    int days()
    {
        return 7;
    }
}
