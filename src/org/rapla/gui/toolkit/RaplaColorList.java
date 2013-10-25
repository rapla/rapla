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
package org.rapla.gui.toolkit;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

/** WARNING: This class is about to change its API. Dont use it */
final public class RaplaColorList {
	public final static Color[] COLORS=
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
				
		getColorForHex("#a3ddff"),		// light blue
		getColorForHex("#b5e97e"),		// light green
		getColorForHex("#ffb85e"),		// orange
		getColorForHex("#b099dc"),		// violet
		getColorForHex("#cccccc"),		// light grey
		getColorForHex("#fef49d"),		// yellow
		getColorForHex("#fc9992"),		// red
    	
    };

    public final static Color[] APPOINTMENT_COLORS=
    {
    	new Color(0xee, 0xee, 0xcc),
    	new Color(0xcc, 0x99, 0xcc),
    	new Color(0xad, 0xac, 0xa2),
    	new Color(0xcc, 0xaa, 0x66),
    	new Color(0xcc, 0xff, 0x88),
    };
    public final static String DEFAULT_COLOR_AS_STRING = getHexForColor( COLORS[0]);


    private static ArrayList<Color> colors = new ArrayList<Color>(Arrays.asList(COLORS));
    private static ArrayList<Color> appointmentColors = new ArrayList<Color>(Arrays.asList(APPOINTMENT_COLORS));
    private static Random randomA = null;
    private static Random randomB = null;

    static private float rndA()
    {
    	if (randomA == null)
    	    randomA = new Random(7913);
    	return (float) (0.45 + randomA.nextFloat()/2.0);
    }


    static private float rndB()
    {
    	if (randomB == null)
    	    randomB = new Random(5513);
    	return (float) (0.4 + randomB.nextFloat()/2.0);
    }

    final static public Color getResourceColor(int nr)
    {
    	if (colors.size()<=nr) {
    	    int fillSize = nr - colors.size() + 1;
    	    for (int i=0;i<fillSize;i++) {
    		colors.add(new Color( (float) (rndA()/1.2)
    				      ,rndA()
    				      ,rndA()
    				      )
    			   );
    	    }
    	}
    	return colors.get(nr);
    }

    final static public Color getAppointmentColor(int nr)
    {
    	if (appointmentColors.size()<=nr) {
    	    int fillSize = nr - appointmentColors.size() + 1;
    	    for (int i=0;i<fillSize;i++) {
    		appointmentColors.add(new Color( (float) (0.1 + rndB() /1.1)
    						 ,rndB()
    						 ,rndB()
    						 )
    				      );
    	    }
    	}
    	return appointmentColors.get(nr);
    }



    public static String getHexForColor(Color color) {
        if ( color == null)
            return "";

        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

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

    private static int decode(String value) {
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

    public static Color getColorForHexOrNull(String hexString)  {
        if ( hexString != null ) {
            try {
                return RaplaColorList.getColorForHex(hexString );
            } catch ( NumberFormatException ex ) {
            }
        }
        return null;
    }

    public static Color getColorForHex(String hexString) throws NumberFormatException {
        if ( hexString == null || hexString.indexOf('#') != 0 || hexString.length()!= 7 )
            throw new NumberFormatException("Can't parse HexValue " + hexString);
        String rString = hexString.substring(1,3).toUpperCase();
        String gString = hexString.substring(3,5).toUpperCase();
        String bString = hexString.substring(5,7).toUpperCase();
        int r = decode( rString);
        int g = decode( gString);
        int b = decode( bString);
        return new Color(r, g, b);
    }


    public static Color darken(Color color, int i) {
        int newBlue = Math.max(  color.getBlue() - i, 0);
        int newRed = Math.max(  color.getRed() - i, 0);
        int newGreen = Math.max(  color.getGreen() - i, 0);
        return new Color( newRed, newGreen,newBlue,  color.getAlpha());
    }

}
