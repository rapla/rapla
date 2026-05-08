package org.rapla.plugin.exchangeconnector.extensionpoints;

import org.rapla.entities.User;


public interface ExchangeConfigExtensionPoint
{

    boolean isResponsibleFor(User user);
    
    String getExchangeUrl(User user);
    
}
