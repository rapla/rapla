package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces the rule from AGENTS.md §15: every {@code @RestController} in rapla
 * is mounted under {@code /api/}, with a hard-coded allow-list of the 6
 * exceptions whose wire URLs are pinned by external constraints
 * (HTML chooser landing, form login, legacy iCal subscribers, JNLP launcher,
 * status HTML page).
 *
 * <p>If this test fails, either you forgot the {@code /api/} prefix on a new
 * controller (the common case — add it) or you genuinely need a new exception
 * (rare — document why in AGENTS.md and add the FQCN to {@link #ALLOWED_NON_API}).
 */
class ApiPrefixArchitectureTest
{
    /**
     * Controllers permitted to live outside {@code /api/}. Each entry must have
     * a documented reason in PRD 031 or AGENTS.md.
     */
    private static final Set<String> ALLOWED_NON_API = Set.of(
            "org.rapla.server.spring.web.IndexPageController",       // /, /index — chooser landing
            "org.rapla.server.spring.LoginPageController",           // /login — Spring form-login convention
            "org.rapla.server.spring.web.CalendarPageController",    // /rapla/calendar* — external iCal subscribers (HARD)
            "org.rapla.server.spring.web.Export2iCalController",     // /rapla/(internal_)?ical — external subscribers (HARD)
            "org.rapla.server.spring.web.RaplaJNLPController",       // /raplaclient.jnlp, /webclient/** — Java Web Start
            "org.rapla.server.spring.web.StatusPageController"       // /server — status HTML page
    );

    @Test
    void everyRestControllerIsUnderApiPrefix()
    {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> violations = new ArrayList<>();

        for (String pkg : List.of("org.rapla.server.spring", "org.rapla.plugin"))
        {
            for (var bd : scanner.findCandidateComponents(pkg))
            {
                String fqcn = bd.getBeanClassName();
                if (fqcn == null) continue;
                // Skip nested classes — test fixtures and Spring auto-config can declare
                // throwaway @RestController inner classes (e.g. NewVersionExceptionMappingIntegrationTest$ThrowingController).
                // The rule applies to production top-level controllers only.
                if (fqcn.contains("$")) continue;
                Class<?> cls;
                try { cls = Class.forName(fqcn); }
                catch (ClassNotFoundException e) { continue; }
                // Defensive: skip anything whose source isn't under main/ — test classes
                // also live on the classpath at scan time, and even top-level test
                // controllers (rare) shouldn't trip the production-architecture check.
                java.net.URL src = cls.getProtectionDomain().getCodeSource() != null
                        ? cls.getProtectionDomain().getCodeSource().getLocation()
                        : null;
                if (src != null && src.toString().contains("/test-classes/")) continue;
                if (ALLOWED_NON_API.contains(fqcn)) continue;
                RequestMapping rm = cls.getAnnotation(RequestMapping.class);
                String path = (rm == null || rm.value().length == 0) ? "" : rm.value()[0];
                if (!path.startsWith("/api/") && !path.equals("/api"))
                {
                    violations.add(fqcn + " has @RequestMapping(\"" + path + "\") — must start with /api/");
                }
            }
        }

        if (!violations.isEmpty())
        {
            fail("AGENTS.md §15 violation — @RestController must be under /api/:\n  "
                    + String.join("\n  ", violations)
                    + "\nIf the controller genuinely belongs at root or under /rapla/, document the reason in AGENTS.md and add its FQCN to ApiPrefixArchitectureTest.ALLOWED_NON_API.");
        }
    }

    @Test
    void allowListIsAccurate()
    {
        for (String fqcn : ALLOWED_NON_API)
        {
            try
            {
                Class<?> cls = Class.forName(fqcn);
                assertTrue(
                        cls.isAnnotationPresent(RestController.class)
                                || cls.isAnnotationPresent(org.springframework.stereotype.Controller.class),
                        fqcn + " is in ALLOWED_NON_API but is not a @RestController/@Controller — remove from the list");
            }
            catch (ClassNotFoundException e)
            {
                fail("ALLOWED_NON_API references missing class: " + fqcn + " — remove or fix");
            }
        }
    }
}
