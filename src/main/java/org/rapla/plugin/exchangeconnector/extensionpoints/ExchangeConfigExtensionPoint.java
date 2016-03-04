package org.rapla.plugin.exchangeconnector.extensionpoints;

import org.rapla.entities.User;
import org.rapla.inject.ExtensionPoint;
import org.rapla.inject.InjectionContext;

@ExtensionPoint(context=InjectionContext.server, id="org.rapla.plugin.exchangeconnector.config")
public interface ExchangeConfigExtensionPoint
{

    boolean isResponsibleFor(User user);
    
    String getExchangeUrl(User user);
    
}
