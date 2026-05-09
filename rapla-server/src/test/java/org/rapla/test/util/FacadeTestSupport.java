package org.rapla.test.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.internal.FacadeImpl;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.storage.dbfile.FileOperator;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tier-2 base for tests that need a real {@link RaplaFacade} backed by a
 * {@link FileOperator} on a temp-dir copy of {@code testdefault.xml}. No
 * Spring, no MockMvc, no random port. Each test method gets a fresh facade
 * over a fresh temp file.
 *
 * <p>Mirrors the production wiring in {@code ServerCoreConfig.raplaFacade()}
 * + {@code ServerStorageSelector.createFileOperator()}. If those add a new
 * constructor argument, this class needs the same edit (the @SpringBootTest
 * acceptance ring in rapla-app catches the drift in CI).
 */
public abstract class FacadeTestSupport
{
    protected static final String DEFAULT_FIXTURE = "/testdefault.xml";

    @TempDir Path tempDir;

    protected Logger logger;
    protected FileOperator operator;
    protected RaplaFacade facade;

    /** Override to swap the fixture (e.g. a smaller, hand-written one). */
    protected String fixtureResource() { return DEFAULT_FIXTURE; }

    @BeforeEach
    void setUpFacade() throws Exception
    {
        logger = RaplaBootstrapLogger.createRaplaLogger();
        Path dataFile = tempDir.resolve("rapla-data.xml");
        try (InputStream in = getClass().getResourceAsStream(fixtureResource()))
        {
            if (in == null)
            {
                throw new IllegalStateException(fixtureResource() + " not on classpath");
            }
            Files.copy(in, dataFile, StandardCopyOption.REPLACE_EXISTING);
        }

        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        CommandScheduler scheduler = new DefaultScheduler(logger);

        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());

        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));

        operator = new FileOperator(logger, i18n, raplaLocale, scheduler,
                functionFactoryMap, dataFile.toAbsolutePath().toString(),
                permissionExtensions);
        operator.connect();

        FacadeImpl impl = new FacadeImpl(i18n, scheduler, logger);
        impl.setOperator(operator);
        facade = impl;
    }

    @AfterEach
    void tearDownFacade()
    {
        if (operator != null && operator.isConnected())
        {
            try { operator.disconnect(); } catch (Exception ignored) {}
        }
    }
}
