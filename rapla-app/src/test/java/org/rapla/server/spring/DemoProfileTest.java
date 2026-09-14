package org.rapla.server.spring;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("demo")
class DemoProfileTest extends DemoProfileChecks
{
    @Override
    boolean demo() { return true; }
}
