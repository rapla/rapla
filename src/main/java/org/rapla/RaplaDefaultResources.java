package org.rapla;

import javax.inject.Inject;
import javax.inject.Named;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.xmlbundle.I18nBundle;

public class RaplaDefaultResources {
    I18nBundle i18n;
    static final String ID = "org.rapla.RaplaResources";
    @Inject
    public RaplaDefaultResources(@Named(ID) I18nBundle i18n)
    {
        this.i18n = i18n;
    }
    public String getString(@PropertyKey(resourceBundle = ID) String key)
    {
        return i18n.getString(key);
    }
    public String format(@PropertyKey(resourceBundle = ID) String key, Object... obj)
    {
        return i18n.format(key, obj);
    }


}