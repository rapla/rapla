package org.rapla.server.spring;

import org.junit.jupiter.api.Test;
import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.components.i18n.BundleManager;
import org.rapla.enpoints.RemoteLogger;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.server.TimeZoneConverter;
import org.rapla.server.internal.ServerStorageSelector;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = RaplaSpringBootApplication.class)
class RaplaSpringBootApplicationTest
{
    @Autowired
    ApplicationContext context;

    @Autowired
    RaplaServerProperties properties;

    @Autowired
    BundleManager bundleManager;

    @Autowired
    TimeZoneConverter timeZoneConverter;

    @Autowired
    RaplaResources raplaResources;

    @Autowired
    RaplaSystemInfo raplaSystemInfo;

    @Autowired
    RaplaLocale raplaLocale;

    @Autowired
    CommandScheduler commandScheduler;

    @Autowired
    RemoteLogger remoteLogger;

    @Autowired
    ServerStorageSelector serverStorageSelector;

    @Autowired
    RaplaFacade raplaFacade;

    @Test
    void contextLoads()
    {
        assertNotNull(context);
    }

    @Test
    void serverPropertiesBound()
    {
        assertNotNull(properties);
        assertNotNull(properties.getDbDatasources());
        assertNotNull(properties.getFileDatasources());
        assertNotNull(properties.getServices());
    }

    @Test
    void coreBeansResolve()
    {
        assertNotNull(bundleManager);
        assertNotNull(timeZoneConverter);
        assertNotNull(raplaResources);
        assertNotNull(raplaSystemInfo);
    }

    @Test
    void localeAndSchedulerResolve()
    {
        assertNotNull(raplaLocale);
        assertNotNull(commandScheduler);
    }

    @Test
    void remoteLoggerResolves()
    {
        assertNotNull(remoteLogger);
    }

    @Test
    void serverStorageSelectorResolves()
    {
        assertNotNull(serverStorageSelector);
    }

    @Test
    void raplaFacadeResolves()
    {
        assertNotNull(raplaFacade);
    }
}
