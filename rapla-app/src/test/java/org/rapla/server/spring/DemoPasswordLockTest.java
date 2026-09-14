package org.rapla.server.spring;

import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("demo")
class DemoPasswordLockTest extends DemoPasswordLockChecks
{
    @Override
    boolean demo() { return true; }
}
