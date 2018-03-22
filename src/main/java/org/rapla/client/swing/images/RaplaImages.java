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

package org.rapla.client.swing.images;

import org.rapla.components.i18n.I18nIcon;
import org.rapla.components.i18n.client.swing.SwingIcon;

import javax.swing.ImageIcon;
import java.awt.Image;
import java.awt.Toolkit;
import java.net.URL;

/**
 * Offers direct access to the images. 
 */
public class RaplaImages
{
    public static Image getImage(String filename)
    {
        try
        {
            URL url = RaplaImages.class.getResource(filename);
            if (url == null)
                return null;
            return Toolkit.getDefaultToolkit().createImage(url);
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    public static ImageIcon getIcon(String filename)
    {
        final Image image = getImage(filename);
        if ( image != null)
        {
            return new ImageIcon(image);
        }
        else
        {
            return null;
        }
    }

    public static ImageIcon getIcon(I18nIcon icon) {
        if ( icon == null)
        {
            throw new NullPointerException("icon can't be null");
        }
        return ((SwingIcon)icon).getIcon();
    }

    public static Image getImage(I18nIcon icon) {
        return getIcon(icon).getImage();
    }


}
