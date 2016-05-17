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

import java.awt.Image;
import java.awt.Toolkit;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.jetbrains.annotations.PropertyKey;
import org.rapla.RaplaResources;
import org.rapla.components.util.IOUtil;
import org.rapla.components.xmlbundle.impl.I18nBundleImpl;
import org.rapla.components.xmlbundle.impl.PropertyResourceBundleWrapper;
import org.rapla.components.xmlbundle.impl.ResourceBundleLoader;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.logger.Logger;

/**
 * Offers direct access to the images. 
 */
@Singleton
public class RaplaImages
{
    Map<String, Icon> iconCache = Collections.synchronizedMap(new TreeMap<String, Icon>());
    final Logger logger;
    final ResourceBundle resourceBundle;
    final String className = "org.rapla.client.swing.gui.images.RaplaImages";

    @Inject
    public RaplaImages(Logger logger) throws RaplaInitializationException
    {
        this.logger = logger;
        resourceBundle = ResourceBundleLoader.loadBundle(className);
        if ( resourceBundle == null)
        {
            throw new RaplaInitializationException("Can't find ResourceBundle for class " + className);
        }
    }
    
    public static InputStream getInputStream(String filename)
    {
        return RaplaImages.class.getResourceAsStream(filename);
    }

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

    public ImageIcon getIconFromKey(@PropertyKey(resourceBundle = RaplaResources.BUNDLENAME) String key)
    {
        String iconfile;
        try
        {
            iconfile = resourceBundle.getString(key);
        }
        catch (MissingResourceException ex)
        {
            logger.debug(ex.getMessage()); //BJO
            throw ex;
        }
        try
        {
            ImageIcon icon = (ImageIcon) iconCache.get(iconfile);
            if (icon == null)
            {
                icon = new ImageIcon(loadResource(iconfile), key);
                iconCache.put(iconfile, icon);
            } // end of if ()
            return icon;
        }
        catch (Exception ex)
        {
            String message = "Icon " + iconfile + " can't be created: " + ex.getMessage();
            logger.error(message);
            throw new MissingResourceException(message, className, key);
        }
    }

    private final byte[] loadResource(String fileName) throws IOException
    {
        return IOUtil.readBytes(getResourceFromFile(fileName));
    }

    private URL getResourceFromFile(String fileName) throws IOException
    {
        URL resource = null;
        String base;
        if (resourceBundle == null)
        {
            throw new IOException("Resource Bundle for icons is missing while looking up " + fileName);
        }
        if (resourceBundle instanceof PropertyResourceBundleWrapper)
        {
            base = ((PropertyResourceBundleWrapper) resourceBundle).getName();
        }
        else
        {
            base = resourceBundle.getClass().getName();
        }
        base = base.substring(0, base.lastIndexOf("."));
        base = base.replaceAll("\\.", "/");
        String file = "/" + base + "/" + fileName;
        resource = I18nBundleImpl.class.getResource(file);
        if (resource == null)
            throw new IOException("File '" + fileName + "' not found. " + " in bundle " + className + " It must be in the same location as '" + base + "'");
        return resource;
    }

}
