package org.rapla.plugin.export2ical.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.ICalConfigService;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

import javax.inject.Inject;

@DefaultImplementation(of =ICalConfigService.class,context = InjectionContext.server)
public class ICalConfigServiceImpl implements ICalConfigService {
    final ClientFacade facade;
    RemoteSession remoteSession;

    @Inject
    public ICalConfigServiceImpl(ClientFacade facade, RemoteSession remoteSession)
    {
        this.facade = facade;
        this.remoteSession = remoteSession;
    }

    public DefaultConfiguration getConfig() throws RaplaException {
        User user = remoteSession.getUser();
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

    public DefaultConfiguration getUserDefaultConfig() throws RaplaException {
        if ( !remoteSession.isAuthentified())
        {
            throw new RaplaSecurityException("user not authentified");
        }
        Preferences preferences = facade.getSystemPreferences();
        DefaultConfiguration config = preferences.getEntry( Export2iCalPlugin.ICAL_CONFIG);
        if ( config == null)
        {
            config = (DefaultConfiguration) ((PreferencesImpl)preferences).getOldPluginConfig(Export2iCalPlugin.class.getName());
        }
        return config;
    }


}
