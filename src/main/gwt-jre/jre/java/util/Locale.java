package java.util;

import java.util.Locale;

public class Locale {
    static public final Locale GERMANY = createConstants("de", "DE");
    static public final Locale GERMAN = createConstants("de", "");
    static public final Locale ENGLISH = createConstants("en", "");
    String lang;
    String country;
    
    public Locale(String lang,String country)
    {
    	this.lang = lang;
    	this.country = country;
    }
    
    public String getLanguage()
    {
    	return lang;
    }
    
    public String getCountry()
    {
    	return country;
    }
     
    public static Locale getDefault()
    {
    	return ENGLISH;
    }
    
    private static Locale createConstants(String lang, String country)
    {
    	return new Locale(lang,country);
    }
}
