package org.rapla.server.spring;

import org.rapla.logger.Logger;
import org.rapla.logger.RaplaBootstrapLogger;
import org.rapla.server.internal.ServerContainerContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class LegacyServerBridgeConfig
{
    @Bean
    public Logger raplaLogger()
    {
        return RaplaBootstrapLogger.createRaplaLogger();
    }

    @Bean
    public ServerContainerContext serverContainerContext(RaplaServerProperties properties)
    {
        ServerContainerContext context = new ServerContainerContext();
        for (Map.Entry<String, String> e : properties.getFileDatasources().entrySet())
        {
            context.addFileDatasource(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Boolean> e : properties.getServices().entrySet())
        {
            context.putServiceState(e.getKey(), e.getValue());
        }
        if (properties.getPatchScript() != null)
        {
            context.setPatchScript(properties.getPatchScript());
        }
        return context;
    }
}
