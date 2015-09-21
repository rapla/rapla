package org.rapla;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.Extension;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Date;
import java.util.Locale;

@Extension(provides = I18nBundle.class, id = RaplaResources.ID)
@DefaultImplementation(of=RaplaResources.class, context = {InjectionContext.server, InjectionContext.swing})
@Singleton
public class RaplaResources extends AbstractBundle {
    public static final String ID = "org.rapla";
    public static final String BUNDLENAME = ID + ".RaplaResources";

    @Inject
    public RaplaResources(BundleManager bundleManager)
    {
      super(BUNDLENAME, bundleManager);
    }
    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key)
    {
        return super.getString(key);
    }

    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key,Locale locale)
    {
        return super.getString(key, locale);
    }

    public String format(@PropertyKey(resourceBundle = BUNDLENAME) String key, Object... obj)
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

    public String calendarweek(Date startDate) {
        String format = getString("calendarweek.abbreviation");
        int week = DateTools.getWeekInYear(startDate, getLocale());
        String result = format.replace("{0}", "" + week);
        // old format also works
        result = result.replace("{0,date,w}", "" + week);
        return result;
    }
}