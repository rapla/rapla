package org.rapla.plugin.jndi.internal;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;

@Path("jndi")
public interface JNDIConfig 
{
    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    void test(MailTestRequest job) throws RaplaException;

    @XmlRootElement
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class MailTestRequest
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
    @GET
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    DefaultConfiguration getConfig() throws RaplaException;
}
