package org.rapla;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;

import javax.inject.Inject;
import java.util.Locale;
import java.util.MissingResourceException;

public class RaplaResources extends AbstractBundle {
    public static final String ID = "org.rapla.RaplaResources";
    @Inject
    public RaplaResources(BundleManager loader)
    {
      super(ID, loader);
    }
    public String getString(@PropertyKey(resourceBundle = ID) String key)
    {
        return super.getString(key);
    }

    public String format(@PropertyKey(resourceBundle = ID) String key, Object... obj)
    {
        return super.format(key, obj);
    }

    public String periodFormatWeek(int weeknumber, String periodName)
    {
        return format("period.format.week", weeknumber, periodName);
    }


    public String infoText(String javaversion) {
        String signed = getString("yes");
        return format("info.text",signed,javaversion );
    }
}