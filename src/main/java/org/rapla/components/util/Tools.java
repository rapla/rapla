 /*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copyReservations of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.components.util;

 import java.util.ArrayList;
 import java.util.List;

/** miscellaneous util methods.*/
public abstract class Tools
{
    
    /**  same as new Object[0].*/
    public static final Object[] EMPTY_ARRAY = new Object[0];
    public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
    public static final String[] EMPTY_STRING_ARRAY = new String[0];
    
    /** test if 2 char arrays match. */
    public static boolean match(char[] p1, char[] p2) {
        boolean bMatch = true;
        if (p1.length == p2.length) {
            for (int i = 0; i<p1.length; i++) {
                if (p1[i] != p2[i]) {
                    bMatch = false;
                    break;
                }
            }
        } else {
            bMatch = false;
        }
        return bMatch;
    }

    private static boolean validStart(char c)
    {
        return (c == '_' || c =='-' || Character.isLetter(c)) ?
            true : false;
    }

    private static boolean validLetter(char c) {
        return (validStart(c) || Character.isDigit(c));
    }

    /** Rudimentary tests if the string is a valid xml-tag.*/
    public static boolean isKey(String key) {
        if ( key.equals( "true") || key.equals("false"))
        {
            return false;
        }
    	// A tag name must start with a letter (a-z, A-Z) or an underscore (_) and can contain letters, digits 0-9, the period (.), the underscore (_) or the hyphen (-). 
        char[] c = key.toCharArray();
        if (c.length == 0)
            return false;
        if (!validStart(c[0]))
            return false;
        for (int i=0;i<c.length;i++) {
            if (!validLetter(c[i]))
                return false;
        }
        return true;
    }

    public static String makeValidKey(String key)
    {

        StringBuilder validKey = new StringBuilder();
        if ( key.isEmpty())
        {
            return "_";
        }

        for (int i=0;i<key.length();i++)
        {
            char c = key.charAt( i);
            if (!validLetter( c))
            {
                validKey.append("_");
            }
            //if it start with a number
            else if ( i== 0 && !validStart( c) )
            {
                validKey.append("_");
                validKey.append(c);
            }
            else
            {
                validKey.append(c);
            }
        }
        return validKey.toString();

    }

    /** same as substring(0,width-1) except that it will not
        not throw an <code>ArrayIndexOutOfBoundsException</code> if string.length()&lt;width.
     */
    public static String left(String string,int width) {
        return string.substring(0, Math.min(string.length(), width -1));
    }

    /** Convert a byte array into a printable format containing aString of hexadecimal digit characters (two per byte).
     * This method is taken form the apache jakarata
     * tomcat project.
     */
    public static String convert(byte bytes[]) {
        StringBuffer sb = new StringBuffer(bytes.length * 2);
        for (int i = 0; i < bytes.length; i++) {
            sb.append(convertDigit(bytes[i] >> 4));
            sb.append(convertDigit(bytes[i] & 0x0f));
        }
        return (sb.toString());
    }

    /** Convert the specified value (0-15) to the corresponding hexadecimal digit.
     * This method is taken form the apache jakarata tomcat project.
     */
    public static char convertDigit(int value) {
        value &= 0x0f;
        if (value >= 10)
            return ((char) (value - 10 + 'a'));
        else
            return ((char) (value + '0'));
    }

    public static boolean equalsOrBothNull(Object o1, Object o2) {
        if (o1 == null) {
            if (o2 != null) {
                return false;
            }
        } else if ( o2 == null) {
            return false;
        } else if (!o1.equals( o2 ) ) {
            return false;
        }
        return true;
    }

    /** 1.3 compatibility method */
    public static String[] split(String stringToSplit, char delimiter) {
        List<String> keys = new ArrayList<>();
        int lastIndex = 0;
        while( true ) {
            int index = stringToSplit.indexOf( delimiter,lastIndex);
            if ( index < 0)
            {
                String token = stringToSplit.substring( lastIndex  );
                if ( token.length() >= 0)
                {
                    keys.add( token );
                }
                break;
            }
            String token = stringToSplit.substring( lastIndex , index );
            keys.add( token );
            lastIndex = index + 1;
        }
        return keys.toArray( new String[] {});

    }

//    /** 1.3 compatibility method */
//    public static String replaceAll( String string, String stringToReplace, String newString ) {
//        if ( stringToReplace.equals( newString))
//            return string;
//        int length = stringToReplace.length();
//        int oldPos = 0;
//        while ( true ) {
//            int pos = string.indexOf( stringToReplace,oldPos);
//            if ( pos < 0 )
//                return string;
//
//            string = string.substring(0, pos) + newString + string.substring( pos + length);
//            oldPos = pos + 1;
//            if ( oldPos >= string.length()  )
//                return string;
//
//        }
//    }

    public static String createXssSafeString(String value) {
	    return value != null ? value.replaceAll("<","&lt;").replaceAll(">", "&gt;").replaceAll("\"", "'") : null;
	}

    public static String firstCharUp(String s)
    {
        if (s == null)
        {
            return null;
        }
        if (s.length() < 1)
        {
            return s;
        }
        final String result = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        return result;
    }

    /** @returns null if no url */
    public static String getUrl(String value)
    {
        int httpEnd = Math.max( value.indexOf(" ")-1, value.length());
        String url = value.substring(0,httpEnd);
        if ( url.contains(":"))
        {
            return url;
        }
        return null;
        //FIXME mit richtigem URL parsing ersetzen
//
//        try
//        {
//            int httpEnd = Math.max( value.indexOf(" ")-1, value.length());
//            URL url = new URL( );
//            return url.toExternalForm();
//        }
//        catch (MalformedURLException ex)
//        {
//            return null;
//        }
    }
}