package java.util;

import java.util.Locale;

public class Locale {
    static public final Locale GERMANY = createConstants("de", "DE");
    static public final Locale GERMAN = createConstants("de", "");
    static public final Locale ENGLISH = createConstants("en", "");
    String lang;
    String country;
    String variant;
    
    public Locale(String lang)
    {
        this(lang, null);
    }
    public Locale(String lang,String country)
    {
        this(lang, country, null);
    }
    public Locale(String lang,String country, String variant)
    {
        this.lang = lang;
        this.country = country;
        this.variant = variant;
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
