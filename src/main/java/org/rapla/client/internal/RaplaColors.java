/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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
package org.rapla.client.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/** WARNING: This class is about to change its API. Dont use it */
final public class RaplaColors {
	private final static String[] COLORS=
    {
		
		/* 
		 * using hex codes of colorsis easier than using
		 * the Color constructor with separated r, g and b values
		 * 
		 * thus we decided to use the getColorForHex method
		 * which takes a hex String and returns a new Color object
		 * 
		 * in the end this is an array of seven different colors
		 * 
		 */
				
		"#a3ddff",		// light blue
		"#b5e97e",		// light green
		"#ffb85e",		// orange
		"#b099dc",		// violet
		"#cccccc",		// light grey
		"#fef49d",		// yellow
		"#fc9992",		// red
    	
    };

    public final static String DEFAULT_COLOR_AS_STRING =  COLORS[0];


    private static ArrayList<String> colors = new ArrayList<String>(Arrays.asList(COLORS));
    private static Random randomA = null;
    private static Random randomB = null;

    static private float rndA()
    {
    	if (randomA == null)
    	    randomA = new Random(7913);
    	return (float) (0.45 + randomA.nextFloat()/2.0);
    }


    static float rndB()
    {
    	if (randomB == null)
    	    randomB = new Random(5513);
    	return (float) (0.4 + randomB.nextFloat()/2.0);
    }

    final static public String getResourceColor(int nr)
    {
    	if (colors.size()<=nr) 
    	{
    	    int fillSize = nr - colors.size() + 1;
    	    for (int i=0;i<fillSize;i++) 
    	    {
    	    	int r = (int) ((float) (0.1 + rndA() /1.1) * 255);
    	    	int g = (int) (rndA() * 255);
    	    	int b = (int) (rndA() * 255);
    	    	String color = getHex( r , g, b);
    	    	colors.add ( color);
    	    }
    	}
    	return colors.get(nr);
    }
    
    private final static String[] APPOINTMENT_COLORS=
    {
    	"#eeeecc",
    	"#cc99cc",
    	"#adaca2",
    	"#ccaa66",
    	"#ccff88"
    };

    static ArrayList<String> appointmentColors = new ArrayList<String>(Arrays.asList(APPOINTMENT_COLORS));

	final static public String getAppointmentColor(int nr)
	{
		if (appointmentColors.size()<=nr) {
		    int fillSize = nr - appointmentColors.size() + 1;
		    for (int i=0;i<fillSize;i++) 
		    {
    	    	int r = (int) ((float) (0.1 + rndB() /1.1) * 255);
    	    	int g = (int) (rndB() * 255);
    	    	int b = (int) (rndB() * 255);
    	    	String color = getHex( r , g, b);
	    		appointmentColors.add( color );
		    }
		}
		return appointmentColors.get(nr);
	}

    public static String getHex(int r, int g, int b) {
		StringBuffer buf = new StringBuffer();
        buf.append("#");
        printHex( buf, r, 2 );
        printHex( buf, g, 2 );
        printHex( buf, b, 2 );
        return buf.toString();
	}

    /** Converts int to hex string. If the resulting string is smaller than size,
     * it will be filled with leading zeros. Example:
     * <code>printHex( buf,10, 2 )</code> appends "0A" to the string buffer.*/
    static void printHex(StringBuffer buf,int value,int size) {
        String hexString = Integer.toHexString(value);
        int fill = size - hexString.length();
        if (fill>0) {
            for (int i=0;i<fill;i ++)
                buf.append('0');
        }
        buf.append(hexString);

    }

    public static int decode(String value) {
        int result = 0;
        int basis = 1;
        for ( int i=value.length()-1;i>=0;i --) {
            char c = value.charAt( i );
            int number;
            if ( c >= '0' && c<='9') {
                number = c - '0';
            } else if ( c >= 'A' && c<='F') {
                number = (c - 'A') + 10;
            } else {
                throw new NumberFormatException("Can't parse HexValue " + value);
            }
            result += number * basis;
            basis = basis * 16;
        }
        return result;
    }

}
