package org.rapla.client.gwt;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rapla.framework.RaplaException;
import org.rapla.rest.gwtjsonrpc.client.ExceptionDeserializer;
import org.rapla.rest.gwtjsonrpc.client.impl.AbstractJsonProxy;
import org.rapla.rest.gwtjsonrpc.client.impl.EntryPointFactory;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RaplaExceptionDeserializer;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class Rapla implements EntryPoint {
    final Logger logger = Logger.getLogger("componentClass");
    public static final String LOGIN_COOKIE = "raplaLoginToken";
     
    public Rapla()
    {
    }
  
    /**
     * This is the entry point method.
     */
    public void onModuleLoad() {
        setProxy();
        RootPanel.get("raplaPopup").setVisible( false);
        startApplication();
    }
    
    private void setProxy() {
        AbstractJsonProxy.setServiceEntryPointFactory( new EntryPointFactory() {
            @Override
            public String getEntryPoint(Class serviceClass) {
                String name = serviceClass.getName().replaceAll("_JsonProxy", "");
                String url = GWT.getModuleBaseURL() + "../rapla/json/" + name;
                return  url;
            }
        });
        AbstractJsonProxy.setExceptionDeserializer(new ExceptionDeserializer() {
			@Override
			public Exception deserialize(String exception, String message, List<String> parameter) {
				final RaplaExceptionDeserializer raplaExceptionDeserializer = new RaplaExceptionDeserializer();
				final RaplaException deserializedException = raplaExceptionDeserializer.deserializeException(exception, message, parameter);
				return deserializedException;
			}
		});
    }
    private LoginTokens getValidToken() {
        String tokenString = Cookies.getCookie(LOGIN_COOKIE);
        if (tokenString != null)
        {
            // re request the server for refresh token
            LoginTokens token = LoginTokens.fromString(tokenString);
            boolean valid = token.isValid();
            if ( valid)
            {
                logger.log(Level.INFO, "found valid cookie: " + tokenString);
                return token;
            }
        }
        logger.log(Level.INFO, "No valid login token found");
        return null;
    }

    private void startApplication() {
        LoginTokens token = getValidToken();
        if (token != null)
        {
            AbstractJsonProxy.setAuthThoken(token.getAccessToken());
            final MainInjector injector = GWT.create(MainInjector.class);
            Bootstrap bootstrap = injector.getBootstrap();
            bootstrap.load();
        } 
        else
        {
            Window.Location.replace("../rapla?page=auth");
        }
    }
    
   


}