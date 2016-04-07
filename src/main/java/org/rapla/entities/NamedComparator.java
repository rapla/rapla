/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.entities;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

import org.rapla.components.util.Assert;

public class NamedComparator<T extends Named> implements Comparator<T> {
    Locale locale;
    Collator collator;
    public NamedComparator(Locale locale) {
        this.locale = locale;
        Assert.notNull( locale );
        collator = Collator.getInstance(locale);
    }
    public int compare(Named o1,Named o2) {
        if ( o1.equals(o2)) return 0;

        Named r1 =  o1;
        Named r2 =  o2;
        int result = collator.compare(
                                      r1.getName(locale)
                                      ,r2.getName(locale)
                                      );
        if ( result !=0 )
            return result;
        else
            return (o1.hashCode() < o2.hashCode()) ? -1 : 1;
    }
}
