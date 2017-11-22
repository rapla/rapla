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
package org.rapla.entities.domain;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsType;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Category;
import org.rapla.entities.Named;
import org.rapla.entities.RaplaObject;

import java.util.Date;
import java.util.Set;
/**
Most universities and schools are planning for fixed periods/terms
rather than arbitrary dates. Rapla provides support for this periods.
*/
@JsType
public interface Period extends RaplaObject<Period>,Comparable<Period>,Named {
    
    Date getStart();
    Date getEnd();
    TimeInterval getInterval();
    int getWeeks();
    @JsMethod(name = "getPeriodName")
    String getName();
    Set<Category> getCategories();

    boolean contains(Date date);

    String toString();
    Period[] PERIOD_ARRAY = new Period[0];
}








