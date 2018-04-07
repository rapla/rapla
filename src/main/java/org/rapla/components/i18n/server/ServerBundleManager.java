package org.rapla.components.i18n.server;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.I18nIconURL;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.i18n.I18nIcon;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;

@DefaultImplementation(of=BundleManager.class,context = { InjectionContext.server})
@Singleton
public class ServerBundleManager extends AbstractBundleManager {
    @Inject
    public ServerBundleManager()
    {
    }
    @Override
    public I18nIcon getIcon(String packageId, String key) {
        String location = getString(packageId,key);
        return new I18nIconURL(key,location);
    }

}
