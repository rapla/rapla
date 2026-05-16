package org.rapla.server.spring.web;

import org.junit.jupiter.api.Test;
import org.rapla.server.spring.SpringDocGroupsConfig;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Enforces the rules from AGENTS.md §15: every {@code @RestController} in rapla
 * is mounted under {@code /api/} AND belongs to exactly one OpenAPI group
 * (with the deliberate {@code auth ⊆ client} overlap permitted).
 *
 * <p>Allow-list covers the 6 controllers whose wire URLs are pinned by external
 * constraints (HTML chooser landing, form login, legacy iCal subscribers, JNLP
 * launcher, status HTML page).
 *
 * <p>If this test fails, either you forgot the {@code /api/} prefix on a new
 * controller, you forgot to add its path to a group in
 * {@link SpringDocGroupsConfig}, or you put it in two groups (other than the
 * permitted auth/client overlap).
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
            "org.rapla.server.spring.web.StatusPageController",      // /server — status HTML page
            "org.rapla.server.spring.web.StaticOpenApiController"    // /api/v3/api-docs — meta endpoint, describes the API rather than being part of it (PRD 041)
    );

    /**
     * The single permitted multi-group membership: {@code auth} is also a subset
     * of {@code client} so the SPA's generated TypeScript client has login covered.
     * Any other multi-group hit fails the test.
     */
    private static final Set<Set<String>> ALLOWED_GROUP_OVERLAPS = Set.of(
            Set.of("auth", "client")
    );

    @Test
    void everyRestControllerIsUnderApiPrefix()
    {
        List<String> violations = new ArrayList<>();
        for (Class<?> cls : findProductionRestControllers())
        {
            if (ALLOWED_NON_API.contains(cls.getName())) continue;
            String path = classLevelPath(cls);
            if (!path.startsWith("/api/") && !path.equals("/api"))
            {
                violations.add(cls.getName() + " has @RequestMapping(\"" + path + "\") — must start with /api/");
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
    void everyRestControllerBelongsToExactlyOneSpecGroup()
    {
        Map<String, List<String>> groups = loadGroups();
        AntPathMatcher matcher = new AntPathMatcher();

        List<String> violations = new ArrayList<>();
        for (Class<?> cls : findProductionRestControllers())
        {
            if (ALLOWED_NON_API.contains(cls.getName())) continue;
            String classPath = classLevelPath(cls);
            if (classPath.isEmpty()) continue;

            Set<String> matchedGroups = new TreeSet<>();
            for (Map.Entry<String, List<String>> g : groups.entrySet())
            {
                if (groupCovers(matcher, g.getValue(), classPath))
                {
                    matchedGroups.add(g.getKey());
                }
            }

            if (matchedGroups.isEmpty())
            {
                violations.add(cls.getSimpleName() + " (\"" + classPath
                        + "\") is in NO group — add its path to one of " + groups.keySet()
                        + " in SpringDocGroupsConfig");
            }
            else if (matchedGroups.size() > 1 && !ALLOWED_GROUP_OVERLAPS.contains(matchedGroups))
            {
                violations.add(cls.getSimpleName() + " (\"" + classPath
                        + "\") matches " + matchedGroups
                        + " — non-overlapping groups required (the only allowed overlap is " + ALLOWED_GROUP_OVERLAPS + ")");
            }
        }

        if (!violations.isEmpty())
        {
            fail("AGENTS.md §15 violation — every @RestController must belong to exactly one OpenAPI group:\n  "
                    + String.join("\n  ", violations));
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

    // --- helpers ---

    /** Scan rapla packages for production-tier @RestController classes. */
    private static List<Class<?>> findProductionRestControllers()
    {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<Class<?>> out = new ArrayList<>();
        for (String pkg : List.of("org.rapla.server.spring", "org.rapla.plugin"))
        {
            for (var bd : scanner.findCandidateComponents(pkg))
            {
                String fqcn = bd.getBeanClassName();
                if (fqcn == null || fqcn.contains("$")) continue;
                Class<?> cls;
                try { cls = Class.forName(fqcn); }
                catch (ClassNotFoundException e) { continue; }
                java.net.URL src = cls.getProtectionDomain().getCodeSource() != null
                        ? cls.getProtectionDomain().getCodeSource().getLocation()
                        : null;
                if (src != null && src.toString().contains("/test-classes/")) continue;
                out.add(cls);
            }
        }
        return out;
    }

    /** Class-level @RequestMapping value, or "" if none. */
    private static String classLevelPath(Class<?> cls)
    {
        RequestMapping rm = cls.getAnnotation(RequestMapping.class);
        if (rm == null || rm.value().length == 0) return "";
        return rm.value()[0];
    }

    /** Spin up just SpringDocGroupsConfig and read each group's pathsToMatch. */
    private static Map<String, List<String>> loadGroups()
    {
        Map<String, List<String>> out = new LinkedHashMap<>();
        try (AnnotationConfigApplicationContext ctx =
                     new AnnotationConfigApplicationContext(SpringDocGroupsConfig.class))
        {
            for (GroupedOpenApi g : ctx.getBeansOfType(GroupedOpenApi.class).values())
            {
                out.put(g.getGroup(), g.getPathsToMatch());
            }
        }
        return out;
    }

    /**
     * A group's {@code pathsToMatch} "covers" a controller at {@code classPath}
     * iff some pattern matches either {@code classPath} (for empty method paths)
     * or {@code classPath + "/x"} (for method-suffixed endpoints).
     */
    private static boolean groupCovers(AntPathMatcher matcher, List<String> patterns, String classPath)
    {
        String probe = classPath.endsWith("/") ? classPath + "x" : classPath + "/x";
        for (String p : patterns)
        {
            if (matcher.match(p, classPath) || matcher.match(p, probe)) return true;
        }
        return false;
    }
}
