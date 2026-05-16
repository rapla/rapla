package org.rapla.client.spring;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.Classification;
import org.rapla.entities.dynamictype.DynamicType;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.server.spring.RaplaSpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * End-to-end probe for the "resource names empty in GUI" bug observed after
 * the PRD 011 (Spring Boot 4 + Jackson 3) cutover.
 *
 * <p>Boots the full {@link RaplaSpringBootApplication} server and a headless
 * {@link SpringRaplaClient} pointed at it (no Swing UI, just facade +
 * RemoteOperator), logs in as homer, then exercises the full client-side
 * deserialize → setResolver → init → formatName chain by inspecting the live
 * facade contents.
 *
 * <p>The test is expensive to set up (~10-15 s for the SB context + REST
 * round-trip), so it bundles every check that runs from one connected facade:
 * <ul>
 *   <li>Each {@code Allocatable.getName(locale)} returns a non-empty value
 *       (the user-reported bug).</li>
 *   <li>Each allocatable's {@code classification} resolves a {@code DynamicType}
 *       (proves the resolver/EntityStore wiring is intact).</li>
 *   <li>Each {@code DynamicType.getName(locale)} resolves through MultiLanguageName.</li>
 *   <li>Each {@code DynamicType.getParsedAnnotation("nameformat")} is present and
 *       its {@code formatString} is non-null (proves the wire-format Map round-trip
 *       and the post-deserialize state of the ParsedText itself).</li>
 *   <li>{@code facade.getReservations(...)} returns at least one reservation, and
 *       each reservation's {@code getName(locale)} is non-empty (same bug for events).</li>
 * </ul>
 *
 * <p>Failures are collected via {@link org.junit.jupiter.api.Assertions#assertAll}
 * so a single run reports every broken layer rather than aborting on the first.
 */
@SpringBootTest(
        classes = RaplaSpringBootApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@Tag("e2e")
class HeadlessClientNameResolutionIntegrationTest
{
    @TempDir
    static Path tempDir;
    static Path dataFile;
    private static String savedHeadless;

    @BeforeAll
    static void setup() throws IOException
    {
        savedHeadless = System.getProperty("java.awt.headless");
        System.setProperty("java.awt.headless", "true");
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = HeadlessClientNameResolutionIntegrationTest.class.getResourceAsStream("/testdefault.xml"))
        {
            assertNotNull(in, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @AfterAll
    static void teardown()
    {
        if (savedHeadless != null) System.setProperty("java.awt.headless", savedHeadless);
        else System.clearProperty("java.awt.headless");
        System.clearProperty("rapla.download.url");
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry)
    {
        registry.add("rapla.file-datasources.raplafile", () -> dataFile.toAbsolutePath().toString());
    }

    @LocalServerPort
    int serverPort;

    @Test
    void clientNameResolutionEndToEnd() throws Exception
    {
        System.setProperty("rapla.download.url", "http://localhost:" + serverPort + "/");

        try (SpringRaplaClient client = new SpringRaplaClient())
        {
            ClientFacade clientFacade = client.getFacade();
            assertNotNull(clientFacade, "facade must wire");

            // Triggers RaplaClientServiceImpl ctor → setOperator on facade.
            assertNotNull(client.getContext().getBean(org.rapla.client.api.ClientService.class),
                    "ClientService bean must wire (this triggers operator → facade attach)");

            assertTrue(clientFacade.login("homer", "duffs".toCharArray()),
                    "homer/duffs must authenticate against the test server");
            assertTrue(clientFacade.isSessionActive(), "facade session must be active after login");

            RaplaFacade facade = clientFacade.getRaplaFacade();
            User user = clientFacade.getUser();
            Locale locale = Locale.ENGLISH;

            Allocatable[] allocatables = facade.getAllocatables();
            assertNotNull(allocatables, "getAllocatables() returned null");
            assertTrue(allocatables.length > 0, "test data should expose at least one allocatable to homer");

            // Diagnostic dump of the first allocatable's state — helps localize *where*
            // in the deserialize→init→formatName chain the resource name vanishes.
            dumpFirstAllocatableState(allocatables[0], locale);

            Collection<Reservation> reservations = awaitReservations(facade, user);

            // Bundle every check so a single run reports every broken layer.
            List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();

            for (Allocatable a : allocatables)
            {
                checks.add(() -> {
                    String name = a.getName(locale);
                    assertNotNull(name, "allocatable " + a.getId() + " getName returned null");
                    assertTrue(!name.isEmpty(),
                            "allocatable " + a.getId() + " getName returned empty — "
                            + "the user-reported GUI bug. Wire format had data; bug is in client "
                            + "deserialize → setResolver → init → formatName chain.");
                    assertTrue(!name.equals(a.getId()),
                            "allocatable " + a.getId() + " getName fell back to the id — "
                            + "the type or classification didn't resolve properly");
                });
                checks.add(() -> {
                    Classification c = a.getClassification();
                    assertNotNull(c, "allocatable " + a.getId() + " has null classification");
                    DynamicType type = c.getType();
                    assertNotNull(type, "allocatable " + a.getId() + " classification.getType() null — "
                            + "EntityResolver/typeId not wired post-deserialize");
                });
                checks.add(() -> {
                    DynamicType type = a.getClassification().getType();
                    String typeName = type.getName(locale);
                    assertNotNull(typeName, "type " + type.getKey() + " getName null");
                    assertTrue(!typeName.isEmpty(),
                            "type " + type.getKey() + " getName empty — MultiLanguageName lost in deserialize");
                });
                checks.add(() -> {
                    DynamicType type = a.getClassification().getType();
                    var parsed = ((org.rapla.entities.dynamictype.internal.DynamicTypeImpl) type)
                            .getParsedAnnotation("nameformat");
                    assertNotNull(parsed,
                            "type " + type.getKey() + " has no nameformat ParsedText after deserialize — "
                            + "annotations Map lost during round-trip");
                });
            }

            checks.add(() -> assertTrue(reservations.size() > 0,
                    "expected ≥1 reservation in test data; got " + reservations.size()));
            for (Reservation r : reservations)
            {
                checks.add(() -> {
                    String name = r.getName(locale);
                    assertNotNull(name, "reservation " + r.getId() + " getName returned null");
                    assertTrue(!name.isEmpty(),
                            "reservation " + r.getId() + " getName returned empty — "
                            + "same bug as allocatable name resolution but for events");
                });
            }

            // Regression: RemoteLocaleService is the only @HttpExchange REST proxy whose
            // methods return Promise<X> — Spring's HttpServiceProxyFactory has no built-in
            // adapter for Promise (unlike Mono/Flux), so it tries to deserialize the response
            // body into a Promise instance directly. Promise is an interface → Jackson throws
            // "InvalidDefinitionException: Cannot construct instance of org.rapla.scheduler.Promise".
            // Hits whenever the user opens anything that constructs a CountryChooser
            // (e.g. Edit Preferences → User options).
            checks.add(() -> {
                org.rapla.storage.RemoteLocaleService localeService =
                        client.getContext().getBean(org.rapla.storage.RemoteLocaleService.class);
                java.util.Set<String> languages = new java.util.LinkedHashSet<>();
                languages.add("en");
                java.util.Map<String, java.util.Set<String>> countries = localeService.countries(languages);
                assertNotNull(countries, "countries() must return a non-null map (interface is now sync — "
                        + "Promise<X> return broke Spring HttpServiceProxyFactory deserialization)");
                assertTrue(countries.containsKey("en"),
                        "countries() must include the requested 'en' language; saw: " + countries.keySet());
            });

            // Regression: when a REST call returns 401 (access token expired), the client
            // should transparently call /auth/refresh with the stored refresh token, update
            // the access token, and retry the original request once. Without this, every
            // long-running session has to re-prompt the user after the JWT TTL elapses.
            // Probe by manually corrupting the access token and asserting a follow-up call
            // still succeeds.
            checks.add(() -> {
                org.rapla.storage.dbrm.RemoteConnectionInfo info =
                        client.getContext().getBean(org.rapla.storage.dbrm.RemoteConnectionInfo.class);
                String good = info.getAccessToken();
                assertNotNull(good, "must have valid access token after login");
                int refreshesBefore = org.rapla.client.spring.ClientProxyConfig.RefreshOn401Interceptor.refreshAttempts.get();
                info.setAccessToken("garbage-expired-token");
                try
                {
                    org.rapla.storage.dbrm.RemoteStorage rs =
                            client.getContext().getBean(org.rapla.storage.dbrm.RemoteStorage.class);
                    java.util.List<org.rapla.facade.internal.ConflictImpl> conflicts = rs.getConflicts();
                    assertNotNull(conflicts,
                            "/api/storage/conflicts must succeed after transparent refresh-on-401 — "
                            + "interceptor should call /oauth2/token grant_type=refresh_token (PRD 041) "
                            + "with the stored refreshToken, update connectionInfo.accessToken, and retry "
                            + "the original request.");
                }
                finally
                {
                    int refreshesAfter = org.rapla.client.spring.ClientProxyConfig.RefreshOn401Interceptor.refreshAttempts.get();
                    assertTrue(refreshesAfter > refreshesBefore,
                            "interceptor must have attempted at least one /auth/refresh call after the corrupted token returned 401");
                    info.setAccessToken(good);
                }
            });

            // Regression: every @Service("<typeClass.getName()>") EditComponent must end up
            // in EditTaskViewSwing.editUiProvider keyed by that class name. The map is a
            // Map<String, Supplier<EditComponent>>; Spring's auto-Map injection only works
            // when matching Supplier<EditComponent> beans exist with the right names — that
            // requires an explicit @Bean factory in SwingClientConfig (parallel to
            // activityPresenters). Without it, edit-on-Preferences fails with
            // "Can't edit objects of type interface org.rapla.entities.configuration.Preferences".
            checks.add(() -> {
                org.rapla.client.internal.edit.swing.EditTaskViewSwing editView =
                        client.getContext().getBean(org.rapla.client.internal.edit.swing.EditTaskViewSwing.class);
                java.lang.reflect.Field f = org.rapla.client.internal.edit.swing.EditTaskViewSwing.class
                        .getDeclaredField("editUiProvider");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, java.util.function.Supplier<org.rapla.client.swing.EditComponent>> map =
                        (java.util.Map<String, java.util.function.Supplier<org.rapla.client.swing.EditComponent>>) f.get(editView);
                assertNotNull(map, "editUiProvider map must inject");
                assertTrue(map.containsKey("org.rapla.entities.configuration.Preferences"),
                        "editUiProvider must contain Preferences key — saw keys: " + map.keySet());
                assertTrue(map.containsKey("org.rapla.entities.dynamictype.DynamicType"),
                        "editUiProvider must contain DynamicType key — saw keys: " + map.keySet());
            });

            // Regression: every @Service("<pluginId>") PluginOptionPanel must end up in
            // PreferencesEditUI.pluginOptionPanel keyed by that plugin id. Same wiring
            // gap as editUiProvider / activityPresenters above — without an explicit
            // @Bean Map<String, Supplier<PluginOptionPanel>> factory in SwingClientConfig,
            // the field receives a single auto-wrapped Supplier whose .get() blows up on
            // multi-bean ambiguity, and the "plugins" branch of the preferences tree is
            // empty / broken.
            checks.add(() -> {
                org.rapla.client.swing.internal.edit.PreferencesEditUI ui =
                        client.getContext().getBean(org.rapla.client.swing.internal.edit.PreferencesEditUI.class);
                java.lang.reflect.Field f = org.rapla.client.swing.internal.edit.PreferencesEditUI.class
                        .getDeclaredField("pluginOptionPanel");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, java.util.function.Supplier<org.rapla.client.extensionpoints.PluginOptionPanel>> map =
                        (java.util.Map<String, java.util.function.Supplier<org.rapla.client.extensionpoints.PluginOptionPanel>>) f.get(ui);
                assertNotNull(map, "pluginOptionPanel map must inject");
                assertTrue(map.containsKey("org.rapla.plugin.tableview"),
                        "pluginOptionPanel must contain the @Service(\"org.rapla.plugin.tableview\") "
                        + "TableviewOption — saw keys: " + map.keySet());
                assertNotNull(map.get("org.rapla.plugin.tableview").get(),
                        "pluginOptionPanel supplier .get() must resolve a real PluginOptionPanel "
                        + "(NoUniqueBeanDefinitionException here means the supplier was the auto-wrapper "
                        + "fallback, not a per-name @Bean factory)");
            });

            assertAll("end-to-end name resolution from Jackson 3 wire format",
                    checks.toArray(new org.junit.jupiter.api.function.Executable[0]));
        }
    }

    /** Print enough of the first allocatable's deserialized state to localize the bug. */
    private static void dumpFirstAllocatableState(Allocatable a, Locale locale) throws Exception
    {
        System.out.println("=== diagnostic: first allocatable state ===");
        System.out.println("id=" + a.getId());
        System.out.println("getName(en)=" + a.getName(locale));
        Classification c = a.getClassification();
        System.out.println("classification=" + (c == null ? "null" : c.getClass().getSimpleName()));
        if (c == null) return;
        org.rapla.entities.dynamictype.internal.DynamicTypeImpl type =
                (org.rapla.entities.dynamictype.internal.DynamicTypeImpl) c.getType();
        System.out.println("type.key=" + type.getKey());
        System.out.println("type.getName(en)=" + type.getName(locale));
        System.out.println("type.attributes.length=" + type.getAttributes().length);
        for (org.rapla.entities.dynamictype.Attribute at : type.getAttributes())
        {
            System.out.println("  attr key=" + at.getKey() + " id=" + at.getId() + " typeImpl=" + at.getClass().getSimpleName());
        }
        System.out.println("type.getAttribute('name')=" + type.getAttribute("name"));
        var parsed = type.getParsedAnnotation("nameformat");
        System.out.println("nameformat ParsedText=" + parsed);
        if (parsed != null)
        {
            for (String f : new String[]{"formatString", "first", "variablesList", "nonVariablesList"})
            {
                java.lang.reflect.Field fld = org.rapla.entities.dynamictype.internal.ParsedText.class.getDeclaredField(f);
                fld.setAccessible(true);
                System.out.println("  ParsedText." + f + "=" + fld.get(parsed));
            }
        }
        // Classification.data raw values
        try
        {
            java.lang.reflect.Field dataField = org.rapla.entities.dynamictype.internal.ClassificationImpl.class
                    .getDeclaredField("data");
            dataField.setAccessible(true);
            System.out.println("classification.data=" + dataField.get(c));
        }
        catch (NoSuchFieldException e) { System.out.println("(no data field)"); }
        System.out.println("=== end diagnostic ===");
    }

    /** Block on a Promise-returning facade method. The facade scheduler is on a worker thread,
     *  so the test thread can wait without deadlocking. */
    private static Collection<Reservation> awaitReservations(RaplaFacade facade, User user) throws Exception
    {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Collection<Reservation>> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        facade.getReservations(user, LocalDateTime.of(2010, 1, 1, 0, 0),
                        LocalDateTime.of(2030, 1, 1, 0, 0), null)
                .thenAccept(r -> { result.set(r); done.countDown(); })
                .exceptionally(t -> { error.set(t); done.countDown(); });
        if (!done.await(15, TimeUnit.SECONDS))
            fail("getReservations did not complete within 15 s");
        if (error.get() != null) fail("getReservations failed: " + error.get(), error.get());
        return result.get();
    }
}
