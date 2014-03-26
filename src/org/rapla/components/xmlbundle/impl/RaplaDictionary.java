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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;



class RaplaDictionary {
    Map<String,DictionaryEntry> entries = new TreeMap<String,DictionaryEntry>();
    Collection<String> availableLang = new ArrayList<String>();
    String defaultLang;

    public RaplaDictionary(String defaultLang) {
        this.defaultLang = defaultLang;
    }

    public void addEntry(DictionaryEntry entry) throws UniqueKeyException {
        if ( entries.get(entry.getKey()) !=  null) {
            throw new UniqueKeyException("Key '" + entry.getKey() + "' already in map.");
        } // end of if ()

        String[] lang = entry.availableLanguages();
        for ( int i = 0; i<lang.length;i++)
            if (!availableLang.contains(lang[i]))
                availableLang.add(lang[i]);

        entries.put(entry.getKey(),entry);
    }

    public Collection<DictionaryEntry> getEntries() {
        return entries.values();
    }

    public String getDefaultLang() {
        return defaultLang;
    }

    public String[] getAvailableLanguages() {
        return availableLang.toArray(new String[0]);
    }

    public String lookup(String key,String lang) {
        DictionaryEntry entry = getEntry(key);
        if ( entry != null) {
            return entry.get(lang,defaultLang);
        } else {
            return null;
        } // end of else
    }

    public DictionaryEntry getEntry(String key) {
        return  entries.get(key);
    }

}



