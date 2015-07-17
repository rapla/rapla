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

import java.awt.Component;
import java.awt.Frame;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

import org.rapla.framework.logger.Logger;

final public class WebstartIO extends DefaultIO {
    Method lookup;
    Method getDefaultPage;
    Method showPageFormatDialog;
    Method print;
    Method saveFileDialog;
    Method openFileDialog;
    Method getName;
    Method getInputStream;
    Method setContents;
    Method getContents;
    Method showDocument;
    Class<?> unavailableServiceException;
    String basicService = "javax.jnlp.BasicService";
    String printService = "javax.jnlp.PrintService";
    String fileSaveService = "javax.jnlp.FileSaveService";
    String fileOpenService = "javax.jnlp.FileOpenService";
    String clipboardService = "javax.jnlp.ClipboardService";

    public WebstartIO(Logger logger) throws UnsupportedOperationException {
        super( logger);
        try {
            Class<?> serviceManagerC = Class.forName("javax.jnlp.ServiceManager");
            Class<?> printServiceC = Class.forName(printService);
            Class<?> fileSaveServiceC = Class.forName(fileSaveService);
            Class<?> fileOpenServiceC = Class.forName(fileOpenService);
            Class<?> clipboardServiceC = Class.forName(clipboardService);
            Class<?> basicServiceC = Class.forName(basicService);
            Class<?> fileContents = Class.forName("javax.jnlp.FileContents");
            unavailableServiceException = Class.forName("javax.jnlp.UnavailableServiceException");

            getName = fileContents.getMethod("getName", new Class[] {} );
            getInputStream = fileContents.getMethod("getInputStream", new Class[] {} );
            lookup = serviceManagerC.getMethod("lookup", new Class[] {String.class});
            getDefaultPage = printServiceC.getMethod("getDefaultPage",new Class[] {});
            showPageFormatDialog = printServiceC.getMethod("showPageFormatDialog",new Class[] {PageFormat.class});
            print = printServiceC.getMethod("print",new Class[] {Printable.class});
            saveFileDialog = fileSaveServiceC.getMethod("saveFileDialog",new Class[] {String.class,String[].class,InputStream.class,String.class});
            openFileDialog = fileOpenServiceC.getMethod("openFileDialog",new Class[] {String.class,String[].class});
            setContents = clipboardServiceC.getMethod("setContents", new Class[] {Transferable.class});
            getContents = clipboardServiceC.getMethod("getContents", new Class[] {});
            showDocument = basicServiceC.getMethod("showDocument", new Class[] {URL.class});
        } catch (ClassNotFoundException ex) {
        	getLogger().error(ex.getMessage());
            throw new UnsupportedOperationException("Java Webstart not available due to " + ex.getMessage());
        } catch (Exception ex) {
        	getLogger().error(ex.getMessage());
            throw new UnsupportedOperationException(ex.getMessage());
        }
    }

    private Object invoke(String serviceName, Method method, Object[] args) throws Exception {
        Object service;
        try {
            service = lookup.invoke( null, new Object[] { serviceName });
            return method.invoke( service,  args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getTargetException();
        } catch (Exception e) {
            if ( unavailableServiceException.isInstance( e ) ) {
                 throw new UnsupportedOperationException("The java-webstart service " + serviceName + " is not available!");
            }
            throw new UnsupportedOperationException(e.getMessage());
        }
    }

    public PageFormat defaultPage() throws UnsupportedOperationException {
        if ( isJDK1_5orHigher() )
            return super.defaultPage();
        PageFormat format = null;
        try {
            format = (PageFormat) invoke ( printService, getDefaultPage, new Object[] {} ) ;
        } catch (Exception ex) {
            getLogger().error("Can't get print service using default PageFormat." + ex.getMessage());
        }
        if (format == null)
            format = new PageFormat();
        logPaperSize (format.getPaper());
        return format;
    }

    public void setContents(Transferable transferable, ClipboardOwner owner) {
    	try {
    		invoke( clipboardService, setContents, new Object[] {transferable});
    	} catch (Exception ex) {
            throw new UnsupportedOperationException(ex.getMessage());
        }
    }
    
    public Transferable getContents( ClipboardOwner owner) {
        try {
            return (Transferable)invoke( clipboardService, getContents, new Object[] {});
        } catch (Exception ex) {
            throw new UnsupportedOperationException(ex.getMessage());
        }
    }

    public PageFormat showFormatDialog(PageFormat format) throws UnsupportedOperationException {
        if ( isJDK1_5orHigher() )
            return super.showFormatDialog( format );
        logPaperSize (format.getPaper());
        try {
            // format = getPrintService().showPageFormatDialog(format);
            format = (PageFormat) invoke( printService, showPageFormatDialog, new Object[] {format});
        } catch (Exception ex) {
            throw new UnsupportedOperationException(ex.getMessage());
        }
        return format;
    }

    /** in the new JDK we don't need the webstart print service */
    private boolean isJDK1_5orHigher() {
        try {
            String version = System.getProperty("java.version");
            boolean  oldVersion = version.startsWith("1.4") || version.startsWith("1.3");
            //System.out.println( version + " new=" + !oldVersion);
            return !oldVersion;
            //return false;
        } catch (SecurityException ex) {
            return false;
        }
    }

    /**
   Prints an printable object. The format parameter is ignored, if askFormat is not set.
   Call showFormatDialog to set the PageFormat.
   @param askFormat If true a dialog will show up to allow the user to edit printformat.
   */
    public boolean print(Printable printable, PageFormat format, boolean askFormat) throws PrinterException,UnsupportedOperationException  {
        if ( isJDK1_5orHigher() ) {
            return super.print( printable , format, askFormat );
        }
        logPaperSize (format.getPaper());
        if ( askFormat ) {
        	format= showFormatDialog(format);
        }
        try {
            Boolean result = (Boolean) invoke( printService , print, new Object[] { printable });
            return result.booleanValue();
        } catch (PrinterException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnsupportedOperationException(ex.getMessage());
        }
    }

    public String saveAsFileShowDialog(String dir,Printable printable,PageFormat format,boolean askFormat,Component owner, boolean pdf) throws IOException,UnsupportedOperationException {
        if (askFormat) { format= showFormatDialog(format); }
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        callExport(printable,format,out, pdf);
        if (dir == null)
            dir = "";
        if ( pdf){
        	return saveFile( null, dir, new String[] {"pdf"},"RaplaOutput.pdf", out.toByteArray());
        }
        else
        {
        	return saveFile( null, dir, new String[] {"ps"},"RaplaOutput.ps", out.toByteArray());
        }
    }

    public String saveFile(Frame frame,String dir, String[] fileExtensions, String filename, byte[] content) throws IOException {
        try {
            
            InputStream in =  new ByteArrayInputStream( content);
            Object result = invoke( fileSaveService, saveFileDialog, new Object[] {
                    dir
                    ,fileExtensions
                    ,in
                    ,filename
                                            });
            if (result != null)
                return (String) getName.invoke( result, new Object[] {});
            else
                return null;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Can't invoke save Method " + ex.getMessage() );
        }
        
    }

    public FileContent openFile(Frame frame,String dir, String[] fileExtensions) throws IOException {
        try {
            
            Object result = invoke( fileOpenService, openFileDialog, new Object[] {
                    dir
                    ,fileExtensions
                                            });
            
            if (result != null)
            {
                String name = (String) getName.invoke( result, new Object[] {});
                InputStream in = (InputStream) getInputStream.invoke( result, new Object[] {});
                FileContent content = new FileContent();
                content.setName( name );
                content.setInputStream( in );
                return content;
            }

            return null;
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnsupportedOperationException("Can't invoke open Method " + ex.getMessage() );
        }
        
    }

    public boolean openUrl(URL url) throws IOException {
    	try {
            // Lookup the javax.jnlp.BasicService object
    		invoke(basicService,showDocument, new Object[] {url});
    		// Invoke the showDocument method
    		return true;
    	} catch(Exception ex) {
            return false;
        }       
	}
     

}

