/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas, Bettina Lademann                |
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
package org.rapla.client.swing.internal.print;

import java.awt.Component;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.Locale;

import org.rapla.RaplaResources;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;



public class PSExportService extends RaplaGUIComponent implements ExportService {
    public final static String EXPORT_DIR = PSExportService.class.getName() + ".dir";
    IOInterface printInterface;


    public PSExportService(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, IOInterface printInterface){
        super(facade, i18n, raplaLocale, logger);
        this.printInterface = printInterface;
    }


    public boolean export(Printable printable,PageFormat pageFormat,Component parentComponent) throws Exception
    {
        String dir = (String) getSessionMap().get(EXPORT_DIR);
		String file = printInterface.saveAsFileShowDialog
        (
                   dir
                   ,printable
                   ,pageFormat
                   ,false
                   ,parentComponent
                                                        );
        if (file != null)
        {
            getSessionMap().put(EXPORT_DIR,file);
            return true;
        }
        else
        {
        	return false;
        }
    }

    public String getName(Locale locale) {
        return getI18n().getString("weekview.print.postscript");
    }
}
