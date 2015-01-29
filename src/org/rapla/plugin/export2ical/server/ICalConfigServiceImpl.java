package org.rapla.plugin.export2ical.server;

import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.configuration.internal.PreferencesImpl;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.DefaultConfiguration;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.plugin.export2ical.Export2iCalPlugin;
import org.rapla.plugin.export2ical.ICalConfigService;
import org.rapla.server.RemoteMethodFactory;
import org.rapla.server.RemoteSession;
import org.rapla.storage.RaplaSecurityException;

public class ICalConfigServiceImpl implements RemoteMethodFactory<ICalConfigService> {
    final ClientFacade facade;
    
    public ICalConfigServiceImpl(ClientFacade facade)
    {
        this.facade = facade;
    }
    
    @Override
    public ICalConfigService createService(final RemoteSession remoteSession) throws RaplaContextException {
        return new ICalConfigService() {
            @SuppressWarnings("deprecation")
            @Override
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
                Preferences preferences = facade.getSystemPreferences();
                DefaultConfiguration config = preferences.getEntry( Export2iCalPlugin.ICAL_CONFIG);
                if ( config == null)
                {
                    config = (DefaultConfiguration) ((PreferencesImpl)preferences).getOldPluginConfig(Export2iCalPlugin.class.getName());
                }
                return config;
            }

        };
    }

}
