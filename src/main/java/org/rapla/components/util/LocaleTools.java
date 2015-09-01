package org.rapla.components.util;

import java.util.Locale;

/**
 * Created by Christopher on 31.08.2015.
 */
public class LocaleTools {
    public static Locale getLocale(String localeString) {
        String[] parts = localeString.split("_");
        if ( parts.length == 0)
        {
            throw new IllegalArgumentException("not a valid locale" + localeString);
        }
        if ( parts.length == 1)
        {
            return new Locale(parts[0]);
        }
        else if ( parts.length <3)
        {
            return new Locale(parts[0], parts[1]);
        }
        return new Locale(parts[0], parts[1],parts[2]);
    }
}
