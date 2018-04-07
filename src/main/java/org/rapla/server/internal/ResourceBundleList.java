package org.rapla.server.internal;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nBundle;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Singleton public class ResourceBundleList
{
    private Set<String> bundleIds;
    private final BundleManager bundleManager;

    @Inject public ResourceBundleList(Set<I18nBundle> i18nBundles, BundleManager bundleManager)
    {
        this.bundleManager = bundleManager;
        Set<String> i18nBundleIds = new LinkedHashSet<>();
        for (I18nBundle i18n : i18nBundles)
        {
            String packageId = i18n.getPackageId();
            i18nBundleIds.add(packageId);
        }
        this.bundleIds = i18nBundleIds;
    }

    public Map<String, Map<String, String>> getBundles(Locale locale)
    {
        Map<String, Map<String, String>> bundles = new LinkedHashMap<>();
        for (String packageId : bundleIds)
        {
            final Collection<String> keys = bundleManager.getKeys(packageId);
            final LinkedHashMap<String, String> raplaResourceIdMap = new LinkedHashMap<>();
            bundles.put(packageId, raplaResourceIdMap);
            for (String key : keys)
            {
                raplaResourceIdMap.put(key, bundleManager.getString(packageId, key, locale));
            }
        }
        return bundles;
    }
}
