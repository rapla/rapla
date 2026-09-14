package org.rapla;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RaplaSystemInfo bundles must keep one key per line: a missing line break glues the next key into the previous
 * value, which breaks that value and hides the key in that locale.
 */
class RaplaSystemInfoPropertiesTest
{
    private static final String BUNDLE = "org.rapla.RaplaSystemInfo";

    private static List<String> bundleFiles() throws IOException, URISyntaxException
    {
        URL baseBundle = RaplaSystemInfoPropertiesTest.class.getResource("/org/rapla/RaplaSystemInfo.properties");
        assertNotNull(baseBundle, "RaplaSystemInfo.properties must be on the test classpath");
        try (Stream<Path> files = Files.list(Paths.get(baseBundle.toURI()).getParent()))
        {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("RaplaSystemInfo") && n.endsWith(".properties"))
                    .sorted()
                    .toList();
        }
    }

    private static Properties loadFiltered(String fileName) throws IOException
    {
        try (InputStream in = RaplaSystemInfoPropertiesTest.class.getResourceAsStream("/org/rapla/" + fileName))
        {
            assertNotNull(in, fileName + " must be on the test classpath");
            Properties properties = new Properties();
            properties.load(in);
            return properties;
        }
    }

    @Test
    void noValueSwallowsAFollowingKey() throws IOException, URISyntaxException
    {
        List<String> files = bundleFiles();
        assertFalse(files.isEmpty(), "no RaplaSystemInfo bundles on the test classpath");
        Properties base = loadFiltered("RaplaSystemInfo.properties");
        List<String> glued = new ArrayList<>();
        for (String file : files)
        {
            Properties properties = loadFiltered(file);
            for (String key : properties.stringPropertyNames())
            {
                String value = properties.getProperty(key);
                for (String other : base.stringPropertyNames())
                {
                    if (value.matches("(?s).*\\S" + java.util.regex.Pattern.quote(other) + " *=.*"))
                    {
                        glued.add(file + ": " + key + " swallowed " + other);
                    }
                }
            }
        }
        assertTrue(glued.isEmpty(), "missing line break before a key: " + glued);
    }

    @Test
    void everyLocaleResolvesBuildTimeAndACleanLicenseTitle() throws IOException, URISyntaxException
    {
        ResourceBundle.Control noFallback = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
        ResourceBundle base = ResourceBundle.getBundle(BUNDLE, Locale.ROOT, noFallback);
        String buildTime = base.getString("rapla.build");
        for (String file : bundleFiles())
        {
            if (!file.startsWith("RaplaSystemInfo_"))
            {
                continue;
            }
            String language = file.substring("RaplaSystemInfo_".length(), file.length() - ".properties".length());
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, Locale.forLanguageTag(language), noFallback);
            assertEquals(buildTime, bundle.getString("rapla.build"), language + ": rapla.build");
            assertFalse(bundle.getString("licensedialog.title").contains("rapla.build"), language + ": licensedialog.title = " + bundle.getString("licensedialog.title"));
        }
    }
}
