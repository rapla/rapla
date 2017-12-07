package org.rapla.server.internal;

import org.rapla.entities.EntityNotFoundException;
import org.rapla.entities.User;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.rest.server.token.SignedToken;
import org.rapla.rest.server.token.TokenInvalidException;
import org.rapla.rest.server.token.ValidToken;
import org.rapla.server.RaplaKeyStorage;
import org.rapla.storage.RaplaInvalidTokenException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.StorageOperator;
import org.rapla.storage.dbrm.LoginTokens;
import org.rapla.storage.dbrm.RemoteStorage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.Date;

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
            Date now = operator.getCurrentTimestamp();
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
        Date now = operator.getCurrentTimestamp();
        Date validUntil = new Date(now.getTime() + 1000 * accessTokenValiditySeconds);
        String signedToken = null;
        try
        {
            signedToken = accessTokenSigner.newToken(userId, now);
        }
        catch (TokenInvalidException e)
        {
            throw new RaplaException(e.getMessage(), e);
        }

        return new LoginTokens(signedToken, validUntil);

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
        Date now = operator.getCurrentTimestamp();
        String userId = user.getId();
        String generatedAPIKey;
        try
        {
            generatedAPIKey = accessTokenSigner.newToken(userId, now);
        }
        catch (TokenInvalidException e)
        {
            throw new RaplaException(e.getMessage(), e);
        }
        keyStore.storeAPIKey(user, "refreshToken", generatedAPIKey);
        return generatedAPIKey;
    }
}
