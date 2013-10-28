package org.rapla.gui.toolkit;

import java.awt.Color;

public class AWTColorUtil {

 	final static public Color getAppointmentColor(int nr)
	{
 		String string = RaplaColors.getAppointmentColor(nr);
 		Color color = getColorForHex(string);
 		return color;
	}

	public static Color getColorForHex(String hexString) throws NumberFormatException {
	    if ( hexString == null || hexString.indexOf('#') != 0 || hexString.length()!= 7 )
	        throw new NumberFormatException("Can't parse HexValue " + hexString);
	    String rString = hexString.substring(1,3).toUpperCase();
	    String gString = hexString.substring(3,5).toUpperCase();
	    String bString = hexString.substring(5,7).toUpperCase();
	    int r = RaplaColors.decode( rString);
	    int g = RaplaColors.decode( gString);
	    int b = RaplaColors.decode( bString);
	    return new Color(r, g, b);
	}

	public static String getHexForColor(Color color) {
	    if ( color == null)
	        return "";
	
	    int r = color.getRed();
	    int g = color.getGreen();
	    int b = color.getBlue();
	
	    return RaplaColors.getHex(r, g, b);
	}

}
