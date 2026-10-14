package org.rapla.client.swing.internal;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/** PRD 131 R10: the logout icon key resolves to a shipped image. */
class LogoutIconTest
{
    @Test
    void iconLogoutPointsToAnExistingImage() throws Exception
    {
        Properties p = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/org/rapla/RaplaResources.properties"))
        {
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        String path = p.getProperty("icon.logout");
        assertNotNull(path);
        assertNotNull(getClass().getResource("/org/rapla/" + path.trim()), path);
    }
}
