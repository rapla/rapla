package org.springframework.exampleframeworkstub;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixture for PluginApiPathWarningListenerTest. Lives in
 * {@code org.springframework.*} so its FQCN matches the listener's skip
 * prefix for Spring framework / Spring Boot infrastructure controllers (e.g.
 * the real {@code BasicErrorController} that auto-registers {@code /error}
 * on every Spring Boot Web app). The listener should NOT warn about
 * framework-provided controllers — they're not "plugins" in the AGENTS.md
 * §15 sense.
 */
@RestController
public class SpringFakeController
{
    @GetMapping("/error") public String error() { return "stub"; }
}
