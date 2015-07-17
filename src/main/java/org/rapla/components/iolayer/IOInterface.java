/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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

package org.rapla.components.iolayer;

import java.awt.Frame;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

/** The IO layer is an abstraction from the io differences in webstart or desktop mode */
public interface IOInterface {
    String saveAsFileShowDialog(
                                   String toDir
                                   ,Printable printable
                                   ,PageFormat format
                                   ,boolean askFormat
                                   ,java.awt.Component owner
                                   ,boolean pdf
                                   )
        throws IOException,UnsupportedOperationException;

    void saveAsFile(
            Printable printable
            ,PageFormat format
            ,OutputStream out
            ,boolean pdf
                         )
        throws IOException,UnsupportedOperationException;

    boolean print(
            Printable printable               
            ,PageFormat format
            ,boolean askFormat
            
                           )
        throws PrinterException,UnsupportedOperationException;

    PageFormat defaultPage()
        throws UnsupportedOperationException;

    PageFormat showFormatDialog(PageFormat format)
        throws UnsupportedOperationException;

    void setContents(Transferable transferable, ClipboardOwner owner);
    Transferable getContents( ClipboardOwner owner);
    public String saveFile(Frame frame,String dir, String[] fileExtensions,String path, byte[] content) throws IOException;
    public FileContent openFile(Frame frame,String dir, String[] fileExtensions) throws IOException;
            
    public boolean openUrl(final URL url) throws IOException;
    
    boolean supportsPostscriptExport();

    public double INCH_TO_MM = 25.40006;
    public double MM_TO_INCH = 1.0 / INCH_TO_MM;
}





