/*--------------------------------------------------------------------------*
 | Copyright (C) 2026, Christopher Kohlhaas                                 |
 *--------------------------------------------------------------------------*/
package org.rapla.storage.dbrm;

import org.rapla.framework.RaplaException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * PRD 072 Phase 2 — the cookie-credential (model A) side of rapla auth. These
 * endpoints serve the BROWSER surfaces (SPA + API-explorer pages), which carry
 * the rapla JWT in an {@code HttpOnly} {@code access_token} cookie rather than
 * an {@code Authorization: Bearer} header. The existing Bearer-shaped endpoints
 * ({@code POST /api/auth/impersonate}, {@code /oauth2/token}) stay for the
 * Swing/iCal/API-key path.
 *
 * <ul>
 *   <li>{@code GET /api/auth/me} — the current identity (username, name, roles,
 *       impersonation state). Reads the cookie OR the Bearer. 401 without a
 *       valid credential. §12 leak-safe: returns ONLY the caller's own identity.</li>
 *   <li>{@code POST /api/auth/refresh} — reactive-401 refresh. Reads the
 *       path-scoped {@code refresh_token} cookie, validates it, mints a fresh
 *       access JWT, sets a fresh {@code access_token} cookie. 401 on an
 *       invalid/expired refresh token.</li>
 *   <li>{@code POST /api/auth/impersonate/switch} — cookie-shaped impersonation
 *       start: validates the admin cookie, checks
 *       {@code PermissionController.canAdminUser}, mints an impersonation access
 *       JWT, sets it as the {@code access_token} cookie.</li>
 *   <li>{@code POST /api/auth/impersonate/end} — restores the admin's
 *       {@code access_token} cookie (re-mint from the admin's refresh session).</li>
 *   <li>{@code POST /api/auth/logout} — sign-out: EXPIRES both the
 *       {@code access_token} and {@code refresh_token} cookies and invalidates
 *       the session. Spring's default {@code LogoutFilter} only clears
 *       {@code JSESSIONID} on {@code POST /logout} and is unaware of rapla's
 *       stateless auth cookies, so the SPA hits this endpoint instead.</li>
 * </ul>
 */
@HttpExchange("/api/auth")
public interface AuthCookieService
{
    @GetExchange("/me")
    IdentityResponse me() throws RaplaException;

    @PostExchange("/refresh")
    void refresh() throws RaplaException;

    @PostExchange("/impersonate/switch")
    void impersonateSwitch(@RequestParam("target_username") String targetUsername) throws RaplaException;

    @PostExchange("/impersonate/end")
    void impersonateEnd() throws RaplaException;

    @PostExchange("/logout")
    void logout() throws RaplaException;
}
