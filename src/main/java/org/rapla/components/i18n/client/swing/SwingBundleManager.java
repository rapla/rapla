package org.rapla.components.i18n.client.swing;

import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.internal.AbstractBundleManager;
import org.rapla.components.util.IOUtil;
import org.rapla.components.i18n.internal.PropertyResourceBundleWrapper;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.io.IOException;
import java.net.URL;
import java.util.*;


@DefaultImplementation(of=BundleManager.class,context = { InjectionContext.swing})
@Singleton
public class SwingBundleManager extends AbstractBundleManager
{
    Map<String, Icon> iconCache = Collections.synchronizedMap(new TreeMap<String, Icon>());
    final Logger logger;
    @Inject
    public SwingBundleManager(Logger logger)
    {
        this.logger = logger;
    }


    @Override
    public SwingIcon getIcon(String packageId, String key) {
        String location = getString(packageId,key);
        final ImageIcon iconFromKey = getIconFromKey(location, packageId);
        return new SwingIcon(key,iconFromKey);
    }

    private ImageIcon getIconFromKey(String iconfile,String packageId)
    {
        final String key = packageId + "/" + iconfile;
        try
        {
            ResourceBundle resourceBundle = loadLocale( packageId,getLocale() );
            ImageIcon icon = (ImageIcon) iconCache.get(key);
            if (icon == null)
            {
                icon = new ImageIcon(loadResource(resourceBundle,iconfile), key);
                iconCache.put(iconfile, icon);
            } // end of if ()
            return icon;
        }
        catch (Exception ex)
        {
            String message = "Icon " + iconfile + " can't be created: " + ex.getMessage();
            logger.error(message);
            throw new MissingResourceException(message, packageId, key);
        }
    }

    private final byte[] loadResource(ResourceBundle resourceBundle,String fileName) throws IOException
    {
        return IOUtil.readBytes(getResourceFromFile(resourceBundle,fileName));
    }

    private URL getResourceFromFile(ResourceBundle resourceBundle,String fileName) throws IOException
    {
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
            base = getClass().getName();
        }
        base = base.substring(0, base.lastIndexOf("."));
        base = base.replaceAll("\\.", "/");
        String file = "/" + base + "/" + fileName;
        URL resource = getClass().getResource(file);
        if (resource == null)
            throw new IOException("File '" + fileName + "' not found. " + " in bundle. It must be in the same location as '" + base + "'");
        return resource;
    }


}
