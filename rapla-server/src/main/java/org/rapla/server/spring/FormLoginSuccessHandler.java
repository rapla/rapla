package org.rapla.server.spring;

import com.nimbusds.jose.JOSEException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.rapla.entities.User;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

/**
 * PRD 072 Phase 1/4 — the success-handler TAIL of the server-side browser
 * {@code formLogin()} (username + password) flow, the form-login counterpart of
 * {@link org.rapla.server.spring.oauth.OidcLoginSuccessHandler}.
 *
 * <p>Spring's {@code UsernamePasswordAuthenticationFilter} authenticates the
 * credentials against rapla's {@code UserDetailsService} (bridged to
 * {@code RaplaFacade.getUser}) and would, by default, only establish a
 * {@code JSESSIONID} session + redirect. But rapla's {@code /api} surface is
 * stateless-JWT (credential model A): it reads the {@code access_token} cookie /
 * Bearer, NOT the servlet session. Without this handler the browser leaves
 * {@code /login} with a session but no cookie, so the SPA's first
 * {@code GET /api/auth/me} 401s ("No user found in session") and bounces straight
 * back to {@code /login} — an infinite login loop (caught by the Phase-4 browser
 * e2e gate).
 *
 * <p>This handler runs the same rapla TAIL as the OIDC path: resolve the rapla
 * {@link User}, mint a rapla access token + persist the refresh session
 * ({@link RefreshSessionService#issueAndPersist}), and set BOTH the
 * {@code access_token} (Path {@code /}) and path-scoped {@code refresh_token}
 * cookies. Cookies are written BEFORE the superclass commits the redirect.
 * It extends {@link SavedRequestAwareAuthenticationSuccessHandler} so a deep-link
 * that triggered the login is honoured; otherwise it lands on {@code /app/}.
 */
public class FormLoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(FormLoginSuccessHandler.class);

    private final RaplaFacade facade;
    private final RefreshSessionService refreshSessionService;
    private final CookieAuthSupport cookies;

    public FormLoginSuccessHandler(RaplaFacade facade,
                                   RefreshSessionService refreshSessionService,
                                   CookieAuthSupport cookies,
                                   String redirectAfterLogin)
    {
        this.facade = facade;
        this.refreshSessionService = refreshSessionService;
        this.cookies = cookies;
        setDefaultTargetUrl((redirectAfterLogin == null || redirectAfterLogin.isEmpty())
                ? "/app/" : redirectAfterLogin);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException
    {
        try
        {
            User user = resolveUser(authentication.getName());
            RefreshSessionService.IssuedTokens tokens = refreshSessionService.issueAndPersist(user);
            cookies.setAccessTokenCookie(response, tokens.accessToken(), tokens.expiresIn());
            cookies.setRefreshTokenCookie(response, tokens.refreshToken(),
                    RefreshSessionService.REFRESH_TOKEN_TTL_SECONDS);

            // B3: nag a passwordless user to set a password (cookies are already set, so the
            // /change-password page is reachable authenticated). Skippable, not disableable.
            if (passwordChangeRequired(user))
            {
                getRedirectStrategy().sendRedirect(request, response, "/change-password");
                return;
            }
        }
        catch (RaplaException | JOSEException e)
        {
            // No cookie set — the SPA's first /api call will 401 and bounce to /login.
            LOGGER.warn("Could not establish a rapla session after form login for '{}': {}",
                    authentication.getName(), e.getMessage());
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }

    /**
     * Resolve the rapla {@link User} from the authenticated principal. The
     * form-login {@code UserDetailsService} sets the principal name to the user's
     * UUID ({@code user.getId()}), so try the operator's id-resolve first, then
     * fall back to a login-name lookup (mirrors {@code raplaUserDetailsService}).
     */
    private boolean passwordChangeRequired(User user)
    {
        try
        {
            return ((org.rapla.storage.SyncStorageOperator) facade.getOperator()).isPasswordChangeRequired(user);
        }
        catch (Exception e)
        {
            LOGGER.warn("Could not evaluate password-change requirement for '{}': {}", user.getUsername(), e.getMessage());
            return false;
        }
    }

    private User resolveUser(String principalName) throws RaplaException
    {
        User user = facade.getOperator().tryResolve(principalName, User.class);
        if (user == null)
        {
            user = facade.getUser(principalName);
        }
        if (user == null)
        {
            throw new RaplaException("No rapla user for principal '" + principalName + "'");
        }
        return user;
    }
}
