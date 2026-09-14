package org.rapla.server.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.dbfile.FileOperator;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PRD 048 Phase 2 — verifies {@link ReloadService} performs a logical restart:
 * it reloads all data from the store and rebuilds the operator's caches without
 * a JVM/Spring restart, and the operator stays connected throughout.
 *
 * <p>Tier-2: plain JUnit, no Spring. A {@link FileOperator} over a temp copy of
 * {@code testdefault.xml} is the store. The store is mutated out-of-band (the
 * XML file is rewritten on disk, as another pod or an admin edit would) and
 * {@code reload()} must make the facade observe the change.
 */
class ReloadServiceTest
{
    private static final Locale LOCALE = Locale.ENGLISH;

    @TempDir Path tempDir;
    private Path dataFile;
    private FileOperator operator;
    private RaplaFacade facade;

    @BeforeEach
    void setUp() throws Exception
    {
        dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream("/testdefault.xml"))
        {
            assertTrue(in != null, "testdefault.xml must be on the classpath");
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }

        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler();

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        operator = new FileOperator(i18n, raplaLocale, scheduler,
                functionFactoryMap, dataFile.toAbsolutePath().toString(),
                permissionExtensions);
        operator.connect();

        FacadeImpl impl = new FacadeImpl(i18n, scheduler);
        impl.setOperator(operator);
        facade = impl;
    }

    @AfterEach
    void tearDown()
    {
        if (operator != null && operator.isConnected())
        {
            try { operator.disconnect(); } catch (Exception ignored) {}
        }
    }

    @Test
    void reloadReReadsOutOfBandStoreChange() throws Exception
    {
        assertTrue(operator.isConnected());
        assertTrue(hasResourceNamed("Room A66"), "fixture must contain the seed resource");
        assertFalse(hasResourceNamed("Room A99"));

        // Out-of-band mutation: rewrite the backing store on disk, bypassing
        // the operator entirely (as a second pod or a manual edit would).
        String xml = Files.readString(dataFile);
        Files.writeString(dataFile, xml.replace("Room A66", "Room A99"));

        // Until reload the operator still serves the cached pre-change state.
        assertTrue(hasResourceNamed("Room A66"), "cache must not see the change before reload");

        new ReloadService(operator).reload();

        assertTrue(operator.isConnected(), "operator must stay connected after reload");
        assertFalse(hasResourceNamed("Room A66"), "stale cache entry must be gone after reload");
        assertTrue(hasResourceNamed("Room A99"), "reload must re-read the store");
    }

    private boolean hasResourceNamed(String name) throws Exception
    {
        return Arrays.stream(facade.getAllocatables())
                .anyMatch(a -> name.equals(a.getName(LOCALE)));
    }
}
