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
package org.rapla.entities.configuration;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsType;
import org.rapla.entities.Entity;
import org.rapla.entities.Named;
import org.rapla.entities.Ownable;
import org.rapla.entities.RaplaObject;
import org.rapla.entities.Timestamp;
import org.rapla.entities.storage.ReferenceInfo;
import org.rapla.framework.TypedComponentRole;

/** Preferences store user-specific Information.
    You can store arbitrary configuration objects under unique role names.
    Each role can contain 1-n configuration entries.
    @see org.rapla.entities.User
 */
@JsType
public interface Preferences extends Entity<Preferences>,Ownable,Timestamp, Named {
    String ID_PREFIX  = "preferences_";
    ReferenceInfo<Preferences> SYSTEM_PREFERENCES_ID = new ReferenceInfo<>(ID_PREFIX + "0", Preferences.class);
    /** returns if there are any preference-entries */
    boolean isEmpty();
    boolean hasEntry(TypedComponentRole<?> role);
 
    <T extends RaplaObject> T getEntry(TypedComponentRole<T> role);
    @JsMethod(name = "getEntryWithDefault")
    <T extends RaplaObject> T getEntry(TypedComponentRole<T> role, T defaultEntry);
    String getEntryAsString(TypedComponentRole<String> role, String defaultValue);
    Boolean getEntryAsBoolean(TypedComponentRole<Boolean> role, boolean defaultValue);
    Integer getEntryAsInteger(TypedComponentRole<Integer> role, int defaultValue);

    /** puts a new configuration entry to the role.*/
    @JsMethod(name = "putBoolean")
    void putEntry(TypedComponentRole<Boolean> role,Boolean entry);
    @JsMethod(name = "putInteger")
    void putEntry(TypedComponentRole<Integer> role,Integer entry);
    @JsMethod(name = "putString")
    void putEntry(TypedComponentRole<String> role,String entry);
    @JsMethod(name = "putCalendarModelConfiguration")
    void putEntry(TypedComponentRole<CalendarModelConfiguration> role,CalendarModelConfiguration entry);
    @JsMethod(name = "putRaplaMap")
    <T> void putEntry(TypedComponentRole<RaplaMap<T>> role,RaplaMap<T> entry);
    @JsMethod(name = "putRaplaConfiguration")
    void putEntry(TypedComponentRole<RaplaConfiguration> role,RaplaConfiguration entry);
    void removeEntry(String role);

}












