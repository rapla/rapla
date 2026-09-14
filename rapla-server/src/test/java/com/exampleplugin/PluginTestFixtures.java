package com.exampleplugin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Plugin test fixtures for PluginApiPathWarningListenerTest. Lives in a
 * non-{@code org.rapla.*} package so the listener treats it as a plugin
 * (the listener's discriminator is "FQCN does not start with org.rapla.").
 */
public class PluginTestFixtures
{
    /** Plugin controller mapped outside {@code /api/} — must trigger a WARN. */
    @RestController
    @RequestMapping("/admin")
    public static class BadPathPluginController
    {
        @GetMapping("/hello") public String hello() { return "x"; }
    }

    /** Plugin controller mapped under {@code /api/} — must not warn. */
    @RestController
    @RequestMapping("/api/example")
    public static class GoodPathPluginController
    {
        @GetMapping("/hello") public String hello() { return "x"; }
    }
}
