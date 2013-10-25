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

package org.rapla.gui.images;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.InputStream;
import java.net.URL;

import javax.swing.ImageIcon;



/**
 * Offers direct access to the images. 
 */
public class Images
{
  public static InputStream getInputStream(String filename)
  {
    return Images.class.getResourceAsStream(filename);
  }
  
  public static Image getImage(String filename)
  {
    try {
        URL url = Images.class.getResource(filename);
        if ( url == null)
            return null;
        return Toolkit.getDefaultToolkit().createImage( url);
    } catch (Exception ex) {
        return null;
    }
  }
  
  public static ImageIcon getIcon(String filename)
  {
    return new ImageIcon(getImage( filename));
  }
}
