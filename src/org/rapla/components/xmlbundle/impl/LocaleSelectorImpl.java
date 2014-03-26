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
package org.rapla.components.xmlbundle.impl;

import java.util.Locale;
import java.util.Vector;

import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.components.xmlbundle.LocaleSelector;


/** If you want to change the locales during runtime put a LocaleSelector
    in the base-context. Instances of {@link I18nBundleImpl} will then register them-self
    as {@link LocaleChangeListener LocaleChangeListeners}. Change the locale
    with {@link #setLocale} and all bundles will try to load the appropriate resources.
 */
public class LocaleSelectorImpl implements LocaleSelector {
    Locale locale;
    Vector<LocaleChangeListener> localeChangeListeners = new Vector<LocaleChangeListener>();

    public LocaleSelectorImpl() {
        locale = Locale.getDefault();
    }

    public void addLocaleChangeListener(LocaleChangeListener listener) {
        localeChangeListeners.add(listener);
    }
    public void removeLocaleChangeListener(LocaleChangeListener listener) {
        localeChangeListeners.remove(listener);
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
        fireLocaleChanged();
    }

    public Locale getLocale() {
        return this.locale;
    }

    public LocaleChangeListener[] getLocaleChangeListeners() {
        return localeChangeListeners.toArray(new LocaleChangeListener[]{});
    }

    public void setLanguage(String language) {
        setLocale(new Locale(language,locale.getCountry()));
    }

    public void setCountry(String country) {
        setLocale(new Locale(locale.getLanguage(),country));
    }

    public String getLanguage() {
        return locale.getLanguage();
    }

    protected void fireLocaleChanged() {
        if (localeChangeListeners.size() == 0)
            return;
        LocaleChangeListener[] listeners = getLocaleChangeListeners();
        LocaleChangeEvent evt = new LocaleChangeEvent(this,getLocale());
        for (int i=0;i<listeners.length;i++)
            listeners[i].localeChanged(evt);
    }

    /** This Listeners is for the bundles only, it will ensure the bundles are always
        notified first.
    */
    void addLocaleChangeListenerFirst(LocaleChangeListener listener) {
        localeChangeListeners.add(0,listener);
    }


}
