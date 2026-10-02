package org.rapla.storage.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.components.util.DateTools;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;
import org.rapla.storage.dbfile.FileOperator;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * WP25 — Rapla 2 writes disabled conflicts without timestamps (Rapla 3 too), so the XML reader stamps every such element with its "now".
 * That "now" must be the UTC wall-clock every Rapla 3 timestamp uses, not the JVM-local one: in a CEST JVM it lay
 * 2 h in the future and every imported conflict logged "Timestamp in table RAPLA_CONFLICT in the future" on load.
 */
class XmlImportTimestampTest
{
    // testdefault.xml: the first reservation loses its created-at/last-changed (as Rapla 2 writes disabled conflicts), the second keeps them
    private static final String STAMPLESS = "02c84e71-5d80-458a-97ad-86822780dc09";
    private static final String STAMPED = "eb1f500c-1354-420d-99c0-551cced25edf";

    @TempDir
    Path tempDir;

    private TimeZone originalZone;
    private FileOperator operator;

    @BeforeEach
    void pinZone()
    {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"));
    }

    @AfterEach
    void restoreZone()
    {
        try { operator.disconnect(); } catch (Exception ignored) {}
        TimeZone.setDefault(originalZone);
    }

    @Test
    void elementsWithoutTimestampAreStampedWithTheUtcNow() throws Exception
    {
        assertFalse(TimeZone.getDefault().getRawOffset() == 0 && !TimeZone.getDefault().useDaylightTime(), "needs a non-UTC zone");
        connect();
        LocalDateTime utcNow = DateTools.toLocalDateTime(System.currentTimeMillis());

        LocalDateTime stampless = ((Reservation) operator.tryResolve(STAMPLESS, Reservation.class)).getLastChanged();
        LocalDateTime stamped = ((Reservation) operator.tryResolve(STAMPED, Reservation.class)).getLastChanged();
        assertFalse(stampless.isAfter(utcNow), "no timestamp in the XML: stamped with the UTC now " + utcNow + ", not " + stampless);
        assertEquals(LocalDateTime.of(2016, 7, 10, 13, 2, 59, 276_000_000), stamped, "an explicit last-changed is kept");
    }

    private void connect() throws Exception
    {
        String xml;
        try (InputStream in = getClass().getResourceAsStream("/testdefault.xml"))
        {
            xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        xml = xml.replace(" created-at=\"2003-11-21T00:00:00.000Z\" last-changed=\"2003-11-21T00:00:00.000Z\"", "");
        Path dataFile = tempDir.resolve("rapla-data.xml");
        Files.writeString(dataFile, xml);

        AbstractBundleManager bundleManager = new ServerBundleManager();
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());
        Map<String, FunctionFactory> functionFactoryMap = new LinkedHashMap<>();
        functionFactoryMap.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));
        operator = new FileOperator(new RaplaResources(bundleManager), raplaLocale, new DefaultScheduler(),
                functionFactoryMap, dataFile.toAbsolutePath().toString(), permissionExtensions);
        operator.connect();
    }
}
