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
package org.rapla.gui.internal.print;

import java.awt.Component;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.Locale;

import org.rapla.components.iolayer.IOInterface;
import org.rapla.framework.RaplaContext;
import org.rapla.gui.RaplaGUIComponent;



public class PDFExportService extends RaplaGUIComponent implements ExportService {
    public final static String EXPORT_DIR = PDFExportService.class.getName() + ".dir";
    IOInterface printInterface;

 
    public PDFExportService(RaplaContext sm) {
        super(sm);
        printInterface = getService(IOInterface.class);
    }


    public boolean export(Printable printable,PageFormat pageFormat,Component parentComponent) throws Exception
    {
        String dir = (String) getSessionMap().get(EXPORT_DIR);
		boolean isPDF = true;
		String file = printInterface.saveAsFileShowDialog
        (
                   dir
                   ,printable
                   ,pageFormat
                   ,false
                   ,parentComponent
                   , isPDF
                                                        );
        if (file != null)
        {
            getSessionMap().put(EXPORT_DIR,file);
            return true;
        }
        return false;        
    }

    public String getName(Locale locale) {
    	return "PDF";

    }
}
