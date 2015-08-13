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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class DictionaryEntry {
    String key;
    Map<String,String> translations = Collections.synchronizedMap(new TreeMap<String,String>());
    
    public DictionaryEntry(String key) {
        this.key = key;
    }
    public void add(String lang,String value) {
        translations.put(lang,value);
    }

    public String getKey() {
        return key;
    }

    public String get(String lang) {
        return  translations.get(lang);
    }

    public String get(String lang,String defaultLang) {
        String content = translations.get(lang);
        if ( content == null) {
            content = translations.get(defaultLang);
        } // end of if ()

        if ( content == null) {
            Iterator<String> it = translations.values().iterator();
            content = it.next();
        }
        return content;
    }

    public String[] availableLanguages() {
        String[] result = new String[translations.keySet().size()];
        Iterator<String> it = translations.keySet().iterator();
        int i = 0;
        while ( it.hasNext()) {
            result[i++] =  it.next();
        }
        return result;
    }

}


