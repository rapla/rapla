package org.rapla.storage.dbrm;

import org.rapla.ConnectInfo;
import org.rapla.RaplaResources;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.rapla.rest.SerializableExceptionInformation;
import org.rapla.rest.client.AuthenticationException;
import org.rapla.rest.client.CustomConnector;
import org.rapla.rest.client.RemoteConnectException;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;
import org.rapla.storage.RaplaInvalidTokenException;
import org.rapla.storage.RaplaSecurityException;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.function.Supplier;

public class MyCustomConnector implements CustomConnector
{
    private final RemoteConnectionInfo remoteConnectionInfo;
    private final Supplier<RemoteAuthentificationService> authentificationService;
    private final TokenStore tokenStore;
    //private final String errorString;
    private final CommandScheduler commandQueue;
    Supplier<RaplaResources> i18n;
    Logger logger;
    private int wrongLoginCounter=0;

    @Autowired public MyCustomConnector(RemoteConnectionInfo remoteConnectionInfo, Supplier<RaplaResources> i18n,Supplier<RemoteAuthentificationService> authentificationService,
            CommandScheduler commandQueue, Logger logger, TokenStore tokenStore)
    {
        this.remoteConnectionInfo = remoteConnectionInfo;
        this.authentificationService = authentificationService;
        this.tokenStore = tokenStore == null ? TokenStores.noOp() : tokenStore;
        this.commandQueue = commandQueue;
        this.i18n = i18n;
        this.logger = logger.getChildLogger("connector");
    }


    @Override public String reauth(Class proxy) throws Exception
    {
        final boolean isAuthentificationService = proxy.getCanonicalName().contains(RemoteAuthentificationService.class.getCanonicalName());
        if (isAuthentificationService )
        {
            return null;
        }

        // Prefer refresh-token reauth (silent, works for both password and
        // OAuth-logged-in sessions) before falling back to re-running the
        // password login (which needs cached credentials we may not have).
        final String refreshToken = remoteConnectionInfo.getRefreshToken();
        if (refreshToken != null && !refreshToken.isEmpty())
        {
            try
            {
                String newAccessToken = refreshUsingToken(refreshToken);
                if (newAccessToken != null)
                {
                    wrongLoginCounter = 0;
                    return newAccessToken;
                }
            }
            catch (Exception refreshFailed)
            {
                logger.info("refresh-token reauth failed (" + refreshFailed.getMessage()
                        + "), falling back to password reauth");
            }
        }

        // Password reauth — only works if the user logged in with username/password
        // AND we kept the password in memory. For OAuth-logged-in sessions this is
        // null and we just bubble up a session-expired error.
        final RemoteAuthentificationService remoteAuthentificationService = authentificationService.get();
        if ( remoteAuthentificationService == null)
        {
            return  null;
        }
        if ( wrongLoginCounter > 1)
        {
            throw new RaplaSecurityException("Authentication Failure. Maybe your password has changed. Please close rapla and login again");
        }
        final ConnectInfo connectInfo = remoteConnectionInfo.connectInfo;
        if (connectInfo == null || connectInfo.getPassword() == null)
        {
            // OAuth-logged-in session with no usable refresh token. Force re-login.
            throw new RaplaSecurityException("Your session has expired. Please sign in again.");
        }
        final String username = connectInfo.getUsername();
        final String password = new String(connectInfo.getPassword());
        final String connectAs = connectInfo.getConnectAs();
        final LoginTokens loginTokens;
        try {
            loginTokens = remoteAuthentificationService.login(new org.rapla.storage.dbrm.LoginCredentials(username, password, connectAs));
            logger.info("Reauthenticating user " + username + (connectAs != null ? " as " + connectAs : ""));
        } catch (RaplaSecurityException e) {
            wrongLoginCounter++;
            throw e;
        }
        wrongLoginCounter = 0;

        final String accessToken = loginTokens.getAccessToken();
        remoteConnectionInfo.setAccessToken( accessToken);
        if (loginTokens.getRefreshToken() != null)
        {
            remoteConnectionInfo.setRefreshToken(loginTokens.getRefreshToken());
            tokenStore.tryWrite(loginTokens.getRefreshToken());
        }
        return accessToken;
    }

    /**
     * Calls the OAuth2 token endpoint with the stored refresh token and
     * stashes the resulting access + refresh tokens on {@link #remoteConnectionInfo}.
     *
     * <p>PRD 041: refresh consolidated onto {@code /oauth2/token grant_type=refresh_token}
     * (OAuth2 standard form-encoded body). Same endpoint contract as a
     * Keycloak deployment — env-var swap of {@code RAPLA_OAUTH_PUBLIC_BASE_URL}
     * is the only change to point at an external IdP.
     *
     * @return the new access token, or null if refresh isn't available
     */
    private String refreshUsingToken(String refreshToken) throws Exception
    {
        String serverUrl = remoteConnectionInfo.getServerURL();
        if (serverUrl == null || serverUrl.isEmpty()) return null;
        String url = serverUrl + "/oauth2/token";
        String encodedRefresh = java.net.URLEncoder.encode(refreshToken, java.nio.charset.StandardCharsets.UTF_8);
        String body = "grant_type=refresh_token&refresh_token=" + encodedRefresh + "&client_id=rapla-client";
        java.net.http.HttpClient http = java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10)).build();
        java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                .timeout(java.time.Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8))
                .build();
        java.net.http.HttpResponse<String> resp = http.send(req,
                java.net.http.HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2)
        {
            throw new RaplaException("refresh failed (HTTP " + resp.statusCode() + "): " + resp.body());
        }
        String respBody = resp.body();
        String newAccess = extractJsonString(respBody, "accessToken");
        if (newAccess == null) newAccess = extractJsonString(respBody, "access_token");
        if (newAccess == null) throw new RaplaException("refresh response missing accessToken: " + respBody);
        String newRefresh = extractJsonString(respBody, "refreshToken");
        if (newRefresh == null) newRefresh = extractJsonString(respBody, "refresh_token");
        remoteConnectionInfo.setAccessToken(newAccess);
        if (newRefresh != null)
        {
            remoteConnectionInfo.setRefreshToken(newRefresh);
            tokenStore.tryWrite(newRefresh);
        }
        logger.info("refresh-token reauth succeeded against " + url);
        return newAccess;
    }

    /** Minimal JSON field extractor — both rapla and OAuth refresh responses
     *  have flat top-level shape so we can avoid pulling in a JSON dependency. */
    private static String extractJsonString(String body, String field)
    {
        String marker = "\"" + field + "\"";
        int i = body.indexOf(marker);
        if (i < 0) return null;
        int colon = body.indexOf(':', i + marker.length());
        if (colon < 0) return null;
        int firstQuote = body.indexOf('"', colon + 1);
        if (firstQuote < 0) return null;
        int closingQuote = body.indexOf('"', firstQuote + 1);
        while (closingQuote > 0 && body.charAt(closingQuote - 1) == '\\')
        {
            closingQuote = body.indexOf('"', closingQuote + 1);
        }
        if (closingQuote < 0) return null;
        return body.substring(firstQuote + 1, closingQuote);
    }

    @Override public Exception deserializeException(SerializableExceptionInformation exe, int statusCode)

    {
        final String message = exe.getMessage();
        final String exceptionClass = exe.getExceptionClass();
        if ( exceptionClass == null)
        {
            return new RaplaException("An error occured. No additinal Exception information: "+ message);
        }
        if (exceptionClass.equals(RaplaInvalidTokenException.class.getName()))
        {
            return new AuthenticationException(message);
        }
        if ( exceptionClass.equals(RemoteConnectException.class.getName()))
        {
            String server = remoteConnectionInfo.getServerURL();
            final RaplaResources raplaResources = i18n.get();
            String errorString = raplaResources.format("error.connect", server) + " ";
            return new RaplaConnectException(errorString + exe.getMessage());
        }
        final RaplaExceptionDeserializer raplaExceptionDeserializer = new RaplaExceptionDeserializer();
        RaplaException ex = raplaExceptionDeserializer.deserializeException(exe,statusCode);
        return ex;
    }


    @Override
    public <T> CompletablePromise<T> createCompletable()
    {
        return commandQueue.createCompletable();
    }

    @Override
    public <T> Promise<T> call(CommandScheduler.Callable<T> callable)
    {
        return commandQueue.supply( callable);
    }
    /*
    public Exception getConnectError(IOException ex)
    {
        String server = remoteConnectionInfo.getServerURL();
        final RaplaResources raplaResources = i18n.get();
        String errorString = raplaResources.format("error.connect", server) + " ";
        return new RaplaConnectException(errorString + ex.getMessage());
    }
    */

    @Override public String getAccessToken()
    {
        return remoteConnectionInfo.getAccessToken();
    }

    @Override
    public String getFullQualifiedUrl(String relativePath)
    {
        return remoteConnectionInfo.getServerURL() + "/" + relativePath;
    }

    @Override public Logger getLogger()
    {
        return logger;
    }


}
