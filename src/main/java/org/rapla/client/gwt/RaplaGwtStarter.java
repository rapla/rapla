package org.rapla.client.gwt;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.storage.dbrm.LoginTokens;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;


@DefaultImplementation(of=GwtStarter.class,context = InjectionContext.gwt, export = true)
public class RaplaGwtStarter implements GwtStarter
{
    public static final String LOGIN_COOKIE = "raplaLoginToken";

    Bootstrap bootstrapProvider;

    @Inject
    public RaplaGwtStarter(Bootstrap bootstrapProvider)
    {
        this.bootstrapProvider = bootstrapProvider;
    }



    private LoginTokens getValidToken()
    {
        final Logger logger = Logger.getLogger("componentClass");
        String tokenString = Cookies.getCookie(LOGIN_COOKIE);
        if (tokenString != null)
        {
            // re request the server for refresh token
            LoginTokens token = LoginTokens.fromString(tokenString);
            boolean valid = token.isValid();
            if (valid)
            {
                logger.log(Level.INFO, "found valid cookie: " + tokenString);
                return token;
            }
        }
        logger.log(Level.INFO, "No valid login token found");
        return null;
    }

    public void startApplication()
    {
        final LoginTokens token = getValidToken();
        if (token != null)
        {
            RaplaPopups.getProgressBar().setPercent(20);
            Scheduler.get().scheduleFixedDelay(new Scheduler.RepeatingCommand()
            {
                @Override
                public boolean execute()
                {
                    bootstrapProvider.load(token.getAccessToken());
                    return false;
                }
            }, 100);
        }
        else
        {
            final String historyToken = History.getToken();
            final String appendig = historyToken != null && !historyToken.isEmpty() ? "&url=rapla.html#" + historyToken : "";
            Window.Location.replace(GWT.getModuleBaseURL() + "../rapla/auth" + appendig);
        }
    }

}
