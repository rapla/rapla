package org.rapla.client.spring;

import org.junit.jupiter.api.Test;
import org.rapla.RaplaResources;
import org.rapla.RaplaSystemInfo;
import org.rapla.components.i18n.BundleManager;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.StartupEnvironment;
import org.rapla.scheduler.CommandScheduler;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClientConfigTest
{
    @Test
    void clientContextLoads()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ClientConfig.class, ClientProxyConfig.class))
        {
            assertNotNull(context.getBean(BundleManager.class));
            assertNotNull(context.getBean(RaplaResources.class));
            assertNotNull(context.getBean(RaplaSystemInfo.class));
            assertNotNull(context.getBean(RaplaLocale.class));
            assertNotNull(context.getBean(CommandScheduler.class));
            assertNotNull(context.getBean(RaplaFacade.class));
            assertNotNull(context.getBean(ClientFacade.class));
            assertNotNull(context.getBean(StartupEnvironment.class));
            assertNotNull(context.getBean(org.rapla.storage.impl.RaplaLock.class));
            assertNotNull(context.getBean(org.rapla.storage.dbrm.RemoteOperator.class));
            assertNotNull(context.getBean(org.rapla.storage.dbrm.RemoteConnectionInfo.class));
        }
    }
}
