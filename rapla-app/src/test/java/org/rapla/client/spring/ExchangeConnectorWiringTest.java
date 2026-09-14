package org.rapla.client.spring;

import org.junit.jupiter.api.Test;
import org.rapla.plugin.exchangeconnector.ShowExchangeForUser;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression: opening User Options on the Swing client triggered
 *   NoSuchBeanDefinitionException: No qualifying bean of type 'ShowExchangeForUser'
 * because the @Lazy @Service ExchangeConnectorUserOptions has a ShowExchangeForUser
 * constructor parameter that the SwingClientConfig component scan could not satisfy
 * (ShowExchangeForUser had @Autowired on its ctor but no @Service/@Component).
 */
class ExchangeConnectorWiringTest
{
    @Test
    void showExchangeForUserBeanResolves()
    {
        try (SpringRaplaClient client = new SpringRaplaClient())
        {
            assertNotNull(client.getContext().getBean(ShowExchangeForUser.class));
        }
    }
}
