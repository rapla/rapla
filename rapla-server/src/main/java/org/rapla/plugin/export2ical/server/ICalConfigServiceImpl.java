package org.rapla.plugin.export2ical.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.ICalConfigService;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import org.springframework.beans.factory.annotation.Autowired;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;

public class ICalConfigServiceImpl implements ICalConfigService {
    @Autowired
    RaplaFacade facade;
    @Autowired
    RemoteSession remoteSession;
    private final HttpServletRequest request;

    @Autowired
    public ICalConfigServiceImpl(@Context HttpServletRequest request)
    {
        this.request = request;
    }

    public DefaultConfiguration getConfig() throws RaplaException {
        User user = remoteSession.checkAndGetUser(request);
        if ( !user.isAdmin())
        {
            throw new RaplaSecurityException("Access only for admin users");
        }
        Preferences preferences = facade.getSystemPreferences();
        DefaultConfiguration config = preferences.getEntry( Export2iCalPlugin.ICAL_CONFIG);
        if ( config == null)
        {
            config = (DefaultConfiguration) ((PreferencesImpl)preferences).getOldPluginConfig(Export2iCalPlugin.class.getName());
        }
        return config;
    }

    @Override
    public DefaultConfiguration getUserDefaultConfig() throws RaplaException {
        User user = remoteSession.checkAndGetUser( request);
        Preferences preferences = facade.getSystemPreferences();
        DefaultConfiguration config = preferences.getEntry( Export2iCalPlugin.ICAL_CONFIG);
        if ( config == null)
        {
            config = (DefaultConfiguration) ((PreferencesImpl)preferences).getOldPluginConfig(Export2iCalPlugin.class.getName());
        }
        return config;
    }
    
    


}
