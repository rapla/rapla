package com.exampleplugin.hello;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tiny demo endpoint. Maps under {@code /api/...} per AGENTS.md §15 — anything
 * outside {@code /api/} triggers a startup WARN from
 * {@code PluginApiPathWarningListener}.
 *
 * <p>Probe with: {@code curl http://localhost:8051/api/hello}
 */
@RestController
@RequestMapping("/api/hello")
public class HelloController
{
    @GetMapping
    public String hello()
    {
        return "hello from the example plugin";
    }
}
