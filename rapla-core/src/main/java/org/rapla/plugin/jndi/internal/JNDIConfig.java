package org.rapla.plugin.jndi.internal;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Returns synchronous types — see {@link org.rapla.plugin.archiver.ArchiverService}
 * for the rationale (Spring's HttpServiceProxyFactory has no Promise adapter).
 */
@HttpExchange("/api/jndi")
public interface JNDIConfig
{
    @PostExchange
    boolean test(@RequestBody MailTestRequest job) throws RaplaException;

    class MailTestRequest
    {
        private DefaultConfiguration config;
        private String username;
        private String password;

        public MailTestRequest()
        {
        }

        public MailTestRequest(DefaultConfiguration config, String username, String password)
        {
            super();
            this.config = config;
            this.username = username;
            this.password = password;
        }

        public DefaultConfiguration getConfig()
        {
            return config;
        }

        public String getUsername()
        {
            return username;
        }

        public String getPassword()
        {
            return password;
        }

    }
    @GetExchange
    DefaultConfiguration getConfig() throws RaplaException;
}
