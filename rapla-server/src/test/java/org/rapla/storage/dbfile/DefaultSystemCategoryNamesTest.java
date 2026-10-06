package org.rapla.storage.dbfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.rapla.RaplaResources;
import org.rapla.components.i18n.server.ServerBundleManager;
import org.rapla.entities.Category;
import org.rapla.entities.domain.permission.PermissionExtension;
import org.rapla.entities.internal.CategoryImpl;
import org.rapla.entities.domain.permission.impl.RaplaDefaultPermissionImpl;
import org.rapla.entities.dynamictype.internal.StandardFunctions;
import org.rapla.entities.extensionpoints.FunctionFactory;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.DefaultScheduler;
import org.rapla.framework.internal.RaplaLocaleImpl;

/** A fresh installation (no data file) names its stock categories in every shipped language, whatever language the server runs in. */
class DefaultSystemCategoryNamesTest
{
    @TempDir Path tempDir;
    FileOperator operator;

    @AfterEach
    void tearDown() throws Exception
    {
        if (operator != null && operator.isConnected()) operator.disconnect();
    }

    private static String name(Category c, String lang)
    {
        return ((CategoryImpl) c).getName().getName(lang);
    }

    private Category category(String key) throws Exception
    {
        return category(key, "en");
    }

    private Category category(String key, String serverLanguage) throws Exception
    {
        ServerBundleManager bundleManager = new ServerBundleManager();
        bundleManager.setLanguage(serverLanguage);
        RaplaResources i18n = new RaplaResources(bundleManager);
        RaplaLocale raplaLocale = new RaplaLocaleImpl(bundleManager);
        Set<PermissionExtension> permissionExtensions = new LinkedHashSet<>();
        permissionExtensions.add(new RaplaDefaultPermissionImpl());
        Map<String, FunctionFactory> functions = new LinkedHashMap<>();
        functions.put(StandardFunctions.NAMESPACE, new StandardFunctions(raplaLocale));
        operator = new FileOperator(i18n, raplaLocale, new DefaultScheduler(), functions,
                tempDir.resolve("missing.xml").toAbsolutePath().toString(), permissionExtensions);
        operator.connect();
        Category root = operator.getSuperCategory();
        Category found = root.getCategory(key);
        if (found == null) found = root.getCategory("user-groups").getCategory(key);
        if (found == null) found = root.getCategory("periods").getCategory(key);
        assertNotNull(found, key);
        return found;
    }

    @Test
    void stockCategoriesCarryGermanNamesOnAnEnglishServer() throws Exception
    {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("user-groups", "Benutzergruppen");
        expected.put("read-events-from-others", "Die Veranstaltungen anderer sehen");
        expected.put("create-events", "Veranstaltungen anlegen");
        expected.put("exchange-synchronization", "Exchange-Synchronisation");
        expected.put("periods", "Zeiträume");
        expected.put("holiday", "Feiertag");
        for (Map.Entry<String, String> e : expected.entrySet())
        {
            assertEquals(e.getValue(), name(category(e.getKey()), "de"), e.getKey());
            operator.disconnect();
            tempDir.resolve("missing.xml").toFile().delete();
        }
    }

    @Test
    void anotherShippedLanguageIsFilledToo() throws Exception
    {
        RaplaResources i18n = new RaplaResources(new ServerBundleManager());
        String fr = i18n.getString("user-groups", Locale.FRENCH);
        assertEquals(fr, name(category("user-groups"), "fr"));
    }

    @Test
    void englishNamesComeFromTheBundleOnAGermanServer() throws Exception
    {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("user-groups", "User groups");
        expected.put("read-events-from-others", "See events of other users");
        expected.put("create-events", "create events");
        expected.put("exchange-synchronization", "Exchange synchronization");
        expected.put("periods", "Periods");
        expected.put("holiday", "Holiday");
        for (Map.Entry<String, String> e : expected.entrySet())
        {
            assertEquals(e.getValue(), name(category(e.getKey(), "de"), "en"), e.getKey());
            operator.disconnect();
            tempDir.resolve("missing.xml").toFile().delete();
        }
    }
}
