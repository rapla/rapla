package org.rapla.server.internal;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.endpoints.server.token.SignedToken;
import org.rapla.endpoints.server.token.TokenInvalidException;
import org.rapla.endpoints.server.token.ValidToken;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.storage.RaplaInvalidTokenException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteStorage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;

@Singleton
public class TokenHandler
{
    SignedToken accessTokenSigner;
    SignedToken refreshTokenSigner;
    RaplaKeyStorage keyStore;
    StorageOperator operator;

    // 1 Hour until the token expires
    int accessTokenValiditySeconds = 60 * 60;

    @Inject public TokenHandler(RaplaKeyStorage keyStorage, StorageOperator operator) throws RaplaInitializationException
    {
        this.keyStore = keyStorage;
        this.operator = operator;
        String secretKey = keyStorage.getRootKeyBase64();
        try
        {
            accessTokenSigner = new SignedToken(accessTokenValiditySeconds, secretKey);
            refreshTokenSigner = new SignedToken(-1, secretKey);
        }
        catch (Exception e)
        {
            throw new RaplaInitializationException(e.getMessage(), e);
        }

    }

    public User getUserWithAccessToken(String tokenString) throws EntityNotFoundException, RaplaInvalidTokenException
    {
        return getUserWithToken(tokenString, accessTokenSigner);
    }

    /*
    public User getUserWithRefreshToken(String tokenString) throws RaplaException
    {
        return getUserWithToken(tokenString, refreshTokenSigner);
    }
    */

    private User getUserWithToken(String tokenString, SignedToken tokenSigner) throws EntityNotFoundException, RaplaInvalidTokenException
    {
        if (tokenString == null)
        {
            return null;
        }
        final int s = tokenString.indexOf('$');
        if (s <= 0)
        {
            return null;
        }

        final String recvText = tokenString.substring(s + 1);
        try
        {
            java.time.LocalDateTime now = operator.getCurrentTimestampAsLocalDateTime();
            ValidToken checkToken = tokenSigner.checkToken(tokenString, recvText, now);
            if (checkToken == null)
            {
                throw new RaplaInvalidTokenException(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED + " InvalidToken " + tokenString);
            }
        }
        catch (TokenInvalidException e)
        {
            throw new RaplaInvalidTokenException(RemoteStorage.USER_WAS_NOT_AUTHENTIFIED + " InvalidToken " + tokenString);
        }
        String userId = recvText;
        User user = operator.resolve(userId, User.class);
        return user;

    }

    public LoginTokens refresh(String refreshToken) throws RaplaException
    {
        User user = getUserWithToken(refreshToken, refreshTokenSigner);
        Collection<String> apiKeys = keyStore.getAPIKeys(user);
        if (!apiKeys.contains(refreshToken))
        {
            throw new RaplaSecurityException("refreshToken not valid");
        }
        LoginTokens generateAccessToken = generateAccessToken(user);
        return generateAccessToken;
    }

    public LoginTokens generateAccessToken(User user) throws RaplaException
    {
        String userId = user.getId();
        java.time.LocalDateTime now = operator.getCurrentTimestampAsLocalDateTime();
        long validityInSeconds = accessTokenValiditySeconds;
        java.time.LocalDateTime validUntil = now.plusSeconds(validityInSeconds);
        String signedToken = null;
        try
        {
            signedToken = getSignedToken(userId, now);
        }
        catch (TokenInvalidException e)
        {
            throw new RaplaException(e.getMessage(), e);
        }

        return LoginTokens.ofLocalDateTime(signedToken, validUntil);

    }

    public String getSignedToken(String userId, java.time.LocalDateTime now) throws TokenInvalidException {
        return accessTokenSigner.newToken(userId, now);
    }

    public String getRefreshToken(User user) throws RaplaException
    {
        Collection<String> apiKeys = keyStore.getAPIKeys(user);
        String refreshToken;
        if (apiKeys.size() == 0)
        {
            refreshToken = null;
        }
        else
        {
            refreshToken = apiKeys.iterator().next();
        }
        return refreshToken;
    }

    public String regenerateRefreshToken(User user) throws RaplaException
    {
        java.time.LocalDateTime now = operator.getCurrentTimestampAsLocalDateTime();
        String userId = user.getId();
        String generatedAPIKey;
        try
        {
            generatedAPIKey = getSignedToken(userId, now);
        }
        catch (TokenInvalidException e)
        {
            throw new RaplaException(e.getMessage(), e);
        }
        keyStore.storeAPIKey(user, "refreshToken", generatedAPIKey);
        return generatedAPIKey;
    }
}
