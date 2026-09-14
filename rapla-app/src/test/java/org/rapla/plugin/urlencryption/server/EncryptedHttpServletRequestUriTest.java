package org.rapla.plugin.urlencryption.server;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression for the encrypted-URL routing bug (2026-06-18): a request decrypted
 * via {@code ?key=} is wrapped in an {@link EncryptedHttpServletRequest}, which
 * the DispatcherServlet then re-maps to a handler using {@code getRequestURI()}.
 *
 * <p>The override returned the full URL ({@code getRequestURL()}) instead of the
 * path, so Spring tried to map {@code http://host/rapla/calendar} as a path,
 * found no handler, and returned 404 — silently breaking EVERY {@code @Controller}
 * endpoint reachable via {@code ?key=} (encrypted calendar export, dhbw stele).
 * Only manifests under a real servlet container; MockMvc resolves the handler
 * differently and masks it — hence this container-free unit test on the contract.
 *
 * <p>{@code getRequestURI()} must return the path; {@code getRequestURL()} keeps
 * returning the absolute URL.
 */
class EncryptedHttpServletRequestUriTest
{
    /** Hand-rolled double: skip real AES so the test needs no key store / facade. */
    private static class FixedDecryptor extends UrlEncryptor
    {
        FixedDecryptor()
        {
            super(null, null, null);
        }

        @Override
        public synchronized String decrypt(String encrypted, String salt)
        {
            return "a=1";
        }
    }

    @Test
    void getRequestUriReturnsPathNotFullUrl() throws Exception
    {
        MockHttpServletRequest original = new MockHttpServletRequest("GET", "/rapla/calendar");
        original.setServerName("host");
        original.setParameter("key", "blob");
        original.setParameter("salt", "123");

        EncryptedHttpServletRequest wrapped = new EncryptedHttpServletRequest(original, new FixedDecryptor());

        assertEquals("/rapla/calendar", wrapped.getRequestURI(),
                "getRequestURI() must return the path so handler mapping works");
        assertEquals("http://host/rapla/calendar", wrapped.getRequestURL().toString(),
                "getRequestURL() must still return the absolute URL");
    }
}
