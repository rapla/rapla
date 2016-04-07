package org.rapla;

import java.util.Date;
import java.util.Locale;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.components.i18n.AbstractBundle;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.util.DateTools;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.inject.Extension;

@Extension(provides = I18nBundle.class, id = RaplaResources.ID)
@Singleton
public class RaplaResources extends AbstractBundle {
    public static final String ID = "org.rapla";
    public static final String BUNDLENAME = ID + ".RaplaResources";

    @Inject
    public RaplaResources(BundleManager bundleManager)
    {
      super(BUNDLENAME, bundleManager);
    }
    @Override
    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key)
    {
        return super.getString(key);
    }

    @Override
    public String getString(@PropertyKey(resourceBundle = BUNDLENAME) String key,Locale locale)
    {
        return super.getString(key, locale);
    }

    @Override
    public String format(@PropertyKey(resourceBundle = BUNDLENAME) String key, Object... obj)
    {
        return super.format(key, obj);
    }

    // add custom format methods

    // custom format for the calendarweek
    public String calendarweek(Date startDate) {
        String format = getString("calendarweek.abbreviation");
        int week = DateTools.getWeekInYear(startDate, getLocale());
        String result = format.replace("{0}", "" + week);
        // old format also works
        result = result.replace("{0,date,w}", "" + week);
        return result;
    }

    // custom format method for formating the number of week in a period
    public String periodFormatWeek(int weeknumber, String periodName)
    {
        return format("period.format.week", weeknumber, periodName);
    }

    // custom format for info text
    public String infoText(String javaversion) {
        String signed = getString("yes");
        return format("info.text",signed,javaversion );
    }

}