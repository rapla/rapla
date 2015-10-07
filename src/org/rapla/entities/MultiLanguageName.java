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
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;





/** Some entities (especially dynamic-types and attributes)
    can have multiple names to allow easier reuse of created schemas or
    support for multi-language-environments.
    @see MultiLanguageNamed
*/
public class MultiLanguageName implements java.io.Serializable {
    // Don't forget to increase the serialVersionUID when you change the fields
    private static final long serialVersionUID = 1;
    Map<String,String> mapLocales = new TreeMap<String,String>();
    transient private boolean readOnly;
    
    public MultiLanguageName(String language,String translation) {
        setName(language,translation);
    }

    public MultiLanguageName(String[][] data) {
        for (int i=0;i<data.length;i++)
            setName(data[i][0],data[i][1]);
    }

    public MultiLanguageName() {
    }
    
    public void setReadOnly() {
        this.readOnly = true;
    }
    
    public boolean isReadOnly() {
        return this.readOnly;
    }

    public String getName(Locale locale) {
        String lang;
        if ( locale != null)
        {
            lang = locale.getLanguage();
        }
        else
        {
            lang = null;
        }
        return getName( lang);
    }
    
    public String getName(String language) {
    	if ( language == null)
    	{
    		language = "en";
    	}
        String result = mapLocales.get(language);
        if (result == null) {
            result = mapLocales.get("en");
        }

        if (result == null) {
            Iterator<String> it = mapLocales.values().iterator();
            if (it.hasNext())
                result =  it.next();
        }
        if (result == null) {
            result = "";
        }
        return result;
    }

    public void setName(String language,String translation) {
        checkWritable();
        setNameWithoutReadCheck(language, translation);
    }
    
    private void checkWritable() {
        if ( isReadOnly() )
            throw new ReadOnlyException("Can't modify this multilanguage name.");
    }

	public void setTo(MultiLanguageName newName) {
        checkWritable();
        mapLocales = new TreeMap<String,String>(newName.mapLocales);
    }
    
    public Collection<String> getAvailableLanguages() {
        return mapLocales.keySet();
    }

    public Object clone() {
        MultiLanguageName newName= new MultiLanguageName();
        newName.mapLocales.putAll(mapLocales);
        return newName;
    }

    public String toString() {
        return getName("en");
    }
    
    @Deprecated
	public void setNameWithoutReadCheck(String language, String translation) {
        if (translation != null && !translation.trim().equals("")) {
            mapLocales.put(language,translation.trim());
        } else {
            mapLocales.remove(language);
        }
	}
}




