package org.rapla.server.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Secure flag: unset follows the request scheme (http intranet installs keep the cookie), an explicit setting wins. */
class CookieAuthSupportTest
{
    @AfterEach
    void clear()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void request(boolean https)
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(https);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static boolean secure(Boolean configured)
    {
        return new CookieAuthSupport(configured).buildAccessCookie("t", 60).isSecure();
    }

    @Test
    void sessionCookiesCarryNoMaxAgeRememberedOnesDo()
    {
        request(false);
        CookieAuthSupport cookies = new CookieAuthSupport(null);
        org.springframework.mock.web.MockHttpServletResponse remembered = new org.springframework.mock.web.MockHttpServletResponse();
        cookies.setAccessTokenCookie(remembered, "a", 3600, true);
        cookies.setRefreshTokenCookie(remembered, "r", 1814400, true);
        assertEquals(3600, remembered.getCookie("access_token").getMaxAge());
        assertEquals(1814400, remembered.getCookie("refresh_token").getMaxAge());

        org.springframework.mock.web.MockHttpServletResponse session = new org.springframework.mock.web.MockHttpServletResponse();
        cookies.setAccessTokenCookie(session, "a", 3600, false);
        cookies.setRefreshTokenCookie(session, "r", 1814400, false);
        assertEquals(-1, session.getCookie("access_token").getMaxAge());
        assertEquals(-1, session.getCookie("refresh_token").getMaxAge());
        assertEquals("/api/auth/session", session.getCookie("refresh_token").getPath());
        assertTrue(session.getCookie("refresh_token").isHttpOnly());
    }

    @Test
    void onlyTheOneValueCountsAsRemembered()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertFalse(CookieAuthSupport.isRemembered(request));
        request.setCookies(new jakarta.servlet.http.Cookie(CookieAuthSupport.REMEMBER_CHOICE_COOKIE, "yes"));
        assertFalse(CookieAuthSupport.isRemembered(request));
        request.setCookies(new jakarta.servlet.http.Cookie(CookieAuthSupport.REMEMBER_CHOICE_COOKIE, "1"));
        assertTrue(CookieAuthSupport.isRemembered(request));
    }

    @Test
    void unsetOnHttpIsNotSecure()
    {
        request(false);
        assertFalse(secure(null));
    }

    @Test
    void unsetOnHttpsIsSecure()
    {
        request(true);
        assertTrue(secure(null));
    }

    @Test
    void unsetWithoutARequestIsSecure()
    {
        assertTrue(secure(null));
    }

    @Test
    void explicitTrueWinsOnHttp()
    {
        request(false);
        assertTrue(secure(true));
    }

    @Test
    void explicitFalseWinsOnHttps()
    {
        request(true);
        assertFalse(secure(false));
    }
}
