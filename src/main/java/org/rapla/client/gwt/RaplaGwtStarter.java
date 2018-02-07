package org.rapla.client.gwt;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.scheduler.Promise;
import org.rapla.storage.dbrm.LoginTokens;

import javax.inject.Inject;
import java.util.logging.Level;
import java.util.logging.Logger;


@DefaultImplementation(of=GwtStarter.class,context = InjectionContext.gwt, export = true)
public class RaplaGwtStarter implements GwtStarter
{
    public static final String LOGIN_COOKIE = "raplaLoginToken";

    Bootstrap bootstrapProvider;

    @Inject
    public RaplaGwtStarter(Bootstrap bootstrapProvider )
    {
        this.bootstrapProvider = bootstrapProvider;
    }


    private LoginTokens getValidToken()
    {
        final Logger logger = Logger.getLogger("componentClass");
        logger.log(Level.INFO,Cookies.getCookieNames().toString());
        String hashToken = Window.Location.getHash();
        String tokenString = null;
        if (hashToken != null && !hashToken.isEmpty()) {
            final int indexOf = hashToken.indexOf(LOGIN_COOKIE);
            if ( indexOf >= 0)
            {
                final String encodedToken = hashToken.substring(indexOf + LOGIN_COOKIE.length() + 1);
                tokenString = encodedToken.replaceAll("&valid_until=","#");
            }
            Cookies.setCookie(LOGIN_COOKIE, tokenString);
        }
        if ( tokenString == null)
        {
            tokenString = Cookies.getCookie(LOGIN_COOKIE);
        }
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
            logger.log(Level.INFO, "No valid login token found");
        }
        else
        {
            logger.log(Level.INFO, "No login token found");
        }
        return null;
    }

    public void startApplication()
    {
//        final LoginTokens token = getValidToken();
//        if (token != null)
//        {
//            RaplaPopups.getProgressBar().setPercent(20);
//            //bootstrapProvider.load(token.getAccessToken());
//            Scheduler.get().scheduleFixedDelay(new Scheduler.RepeatingCommand()
//            {
//                @Override
//                public boolean execute()
//                {
//                    final Promise<Void> load = bootstrapProvider.load(token.getAccessToken());
//                    bootstrapProvider.start( load);
//                    return false;
//                }
//            }, 100);
//        }
//        else
//        {
//            redirectToStart();
//        }
    }

    @Override
    public void registerJavascriptApi()
    {
        System.out.println("Register Start");
        final LoginTokens token = getValidToken();

        if (token != null)
        {
            System.out.println("Token found");
            final String accessToken = token.getAccessToken();

            final Promise<Void> load = bootstrapProvider.load(accessToken);
            load.thenRun(() -> {
                final JsApi api = bootstrapProvider.getAPI();
                new RaplaCallback().callback(api);
            }).exceptionally((ex)->{bootstrapProvider.getLogger().error(ex.getMessage(),ex);return  null;});
        }
        else
        {
            redirectToStart();
        }

    }

    static public void redirectToStart()
    {
        final String historyToken = History.getToken();
        final String appendig = historyToken != null && !historyToken.isEmpty() ? "&url=rapla.html#" + historyToken : "";
        Window.Location.replace(/*GWT.getModuleBaseURL*/ "../rapla/login" + appendig);
    }

}
