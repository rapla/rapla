package org.rapla.server.spring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
