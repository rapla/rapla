package org.rapla.server.internal;

import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.rapla.framework.logger.Logger;

public class RaplaJNDIContext
{
    Logger logger;
    Context env;
    Map<String,String> initParameters;
    
    public RaplaJNDIContext(Logger logger, Map<String, String> initParameters) {
        this.logger = logger;
        this.initParameters = initParameters;
        Context env;
        try
        {
            Context initContext = new InitialContext();
            Context envContext = (Context)initContext.lookup("java:comp");
            env = (Context)envContext.lookup("env");
        } catch (Exception e) {
            env = null;
            getLogger().warn("No JNDI Enivronment found under java:comp or java:/comp");
        }
        this.env = env;
    }

    
    
    public boolean hasContext() {
        return env != null;
    }

    public Logger getLogger() {
        return logger;
    }
    
    public Object lookupResource( String lookupname, boolean log) {
        String newLookupname = initParameters.get(lookupname);
        if (newLookupname != null && newLookupname.length() > 0)
        {
            lookupname = newLookupname;
        }
        Object result = lookup(lookupname, log);
        return result;
    }

    public String lookupEnvString( String lookupname, boolean log) {
        Object result = lookupEnvVariable( lookupname, log);
        return (String) result;
    
    }           
    
    public Object lookupEnvVariable(String lookupname, boolean log) {
        String newEnvname = initParameters.get(lookupname);
        if ( newEnvname != null)
        {
            getLogger().info("Using contextparam for " + lookupname + ": " + newEnvname);
        }

        if (newEnvname != null && newEnvname.length() > 0 )
        {
            return newEnvname;
        }
        else
        {
            Object result = lookup(lookupname, log);
            return result;
        }
    }
    
    public Object lookup( String string, boolean warn) {
        try {
            Object result = env.lookup( string);
            if ( result == null && warn)
            {
                getLogger().warn("JNDI Entry "+ string + " not found");
            }

            return result;
        } catch (Exception e) {
            if ( warn )
            {
                getLogger().warn("JNDI Entry "+ string + " not found");
            }
            return null;
        }
    }

    
}