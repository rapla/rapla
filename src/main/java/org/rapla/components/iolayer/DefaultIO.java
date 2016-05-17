/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender                                     |
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
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.PrintException;
import javax.print.SimpleDoc;
import javax.print.StreamPrintService;
import javax.print.StreamPrintServiceFactory;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.Size2DSyntax;
import javax.print.attribute.standard.MediaName;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import org.rapla.logger.Logger;

@Singleton
public class DefaultIO  implements IOInterface{
    static DocFlavor flavor = DocFlavor.SERVICE_FORMATTED.PRINTABLE;
    /**
     * Name of all Rapla printjobs (used in dialogs, printerqueue, etc).
   */
    public final static String RAPLA_JOB= "Rapla Printjob";
    public PrinterJob job;
    Logger logger;

    @Inject
    public DefaultIO(Logger logger) {
        this.logger =  logger;
    }
    
    public Logger getLogger() {
        return logger;
    }

    private PrinterJob getJob() {
        if (job == null)
            job = PrinterJob.getPrinterJob();
        return job;
    }

    public PageFormat defaultPage() throws UnsupportedOperationException {
        try {
            PageFormat format = getJob().defaultPage();
            return format;
        } catch (SecurityException ex) {
            return new PageFormat();
        }
    }

    public PageFormat showFormatDialog(PageFormat format) throws UnsupportedOperationException {
        logPaperSize (format.getPaper());
        format =  getJob().pageDialog(format);
        logPaperSize (format.getPaper());
        return format;
    }

    public void setContents(Transferable transferable, ClipboardOwner owner) {
    	Toolkit.getDefaultToolkit().getSystemClipboard().setContents(  transferable, owner );
    }

    public Transferable getContents( ClipboardOwner owner) {
        return Toolkit.getDefaultToolkit().getSystemClipboard().getContents(  owner);
    }
    
    public boolean supportsPostscriptExport() {
        if (!supportsPrintService())
            return false;
        return getPSExportServiceFactory() != null;
    }

    private boolean supportsPrintService() {
        try {
            getClass().getClassLoader().loadClass("javax.print.StreamPrintServiceFactory");
            return true;
        } catch (ClassNotFoundException ex) {
            getLogger().warn("No support for javax.print.StreamPrintServiceFactory");
            return false;
        }
    }

    protected void callExport(Printable printable, PageFormat format,OutputStream out) throws UnsupportedOperationException,IOException {
        save(printable,format,out);
    }
    
    private static StreamPrintServiceFactory getPSExportServiceFactory() {
        StreamPrintServiceFactory []factories =
            StreamPrintServiceFactory.lookupStreamPrintServiceFactories(flavor,"application/postscript");
    
        if (factories.length == 0) {
            return null;
        } 
        /*
          for (int i=0;i<factories.length;i++) {
          System.out.println("Stream Factory " + factories[i]);
          System.out.println("  Output " + factories[i].getOutputFormat());
          DocFlavor[] docFlavors = factories[i].getSupportedDocFlavors();
          for (int j=0;j<docFlavors.length;j++) {
          System.out.println("  Flavor " + docFlavors[j].getMimeType());
          }
          }*/
        return factories[0];
    }

    public void save(Printable print,PageFormat format,OutputStream out) throws IOException {
        StreamPrintService sps = null;
        try {
            StreamPrintServiceFactory spsf = getPSExportServiceFactory();
            if (spsf == null)
            {
                throw new UnsupportedOperationException("No suitable factories for postscript-export.");
            }
            sps = spsf.getPrintService(out);
            Doc  doc = new SimpleDoc(print, flavor, null);
            PrintRequestAttributeSet aset = new HashPrintRequestAttributeSet();
            if (format.getOrientation() == PageFormat.LANDSCAPE)
            {
                aset.add(OrientationRequested.LANDSCAPE);
            }
            if (format.getOrientation() == PageFormat.PORTRAIT)
            {
                aset.add(OrientationRequested.PORTRAIT);
            }
            if (format.getOrientation() == PageFormat.REVERSE_LANDSCAPE)
            {
                aset.add(OrientationRequested.REVERSE_LANDSCAPE);
            }
    
            aset.add(MediaName.ISO_A4_WHITE);
            Paper paper = format.getPaper();
            if (sps.getSupportedAttributeValues(MediaPrintableArea.class,null,null) != null)
            {
                MediaPrintableArea printableArea = new MediaPrintableArea
                (
                 (float)(paper.getImageableX()/72)
                 ,(float)(paper.getImageableY()/72)
                 ,(float)(paper.getImageableWidth()/72)
                 ,(float)(paper.getImageableHeight()/72)
                 ,Size2DSyntax.INCH
                 );
                aset.add(printableArea);
                //  System.out.println("new Area: " + printableArea);
            }
            sps.createPrintJob().print(doc,aset);
        } catch (PrintException ex) { 
            throw new IOException(ex.getMessage());
        } finally {
            if (sps != null)
            sps.dispose();
        }
        }

    @Override
    public String saveAsFileShowDialog(String dir,Printable printable,PageFormat format,boolean askFormat,Component owner) throws UnsupportedOperationException,IOException {
        if (askFormat) { format= showFormatDialog(format); }
        JFileChooser chooser = new JFileChooser();
        chooser.setAcceptAllFileFilterUsed(false);
		chooser.setApproveButtonText("Save");
        if (dir != null)
            chooser.setCurrentDirectory(new File(dir));

        // Note: source for ExampleFileFilter can be found in FileChooserDemo,
        // under the demo/jfc directory in the Java 2 SDK, Standard Edition.
        chooser.setFileFilter( new PSFileFilter());
        int returnVal = chooser.showOpenDialog(owner);
        if(returnVal != JFileChooser.APPROVE_OPTION)
            return null;

        File selectedFile = chooser.getSelectedFile();
      
        String suffix = ".ps";
		String name = selectedFile.getName();
		if (!name.endsWith(suffix))
        {
        	String parent = selectedFile.getParent();
			selectedFile = new File( parent, name + suffix);
        }
		OutputStream out = new FileOutputStream(selectedFile);
        callExport(printable,format,out);
        out.close();
        return selectedFile.getPath();
    }

    private class PSFileFilter extends FileFilter {
        public boolean accept(File file) {
            return (file.isDirectory() || file.getName().toLowerCase().endsWith(".ps"));
        }

        public String getDescription() {
            return "Postscript";
        }
    }
    
    private class PDFFileFilter extends FileFilter {
        public boolean accept(File file) {
            return (file.isDirectory() || file.getName().toLowerCase().endsWith(".pdf"));
        }

        public String getDescription() {
            return "PDF";
        }
        
    }
    public void saveAsFile(Printable printable,PageFormat format,OutputStream out) throws UnsupportedOperationException,IOException {
        callExport(printable,format,out);
    }

    /**
   Prints an awt or swing component.
   @param askFormat If true a dialog will show up to allow the user to edit printformat.
   */
    public boolean print(Printable printable, PageFormat format, boolean askFormat) throws PrinterException {
        getJob().setPrintable(printable, format);
        getJob().setJobName(RAPLA_JOB);

        if (askFormat) {
            if (getJob().printDialog()) {
                logPaperSize (format.getPaper());
                getJob().print();
                return true;
            }
        } else {
            getJob().print();
            return true;
        }
        getJob().cancel();
        return false;
    }

    void logPaperSize(Paper paper) {
        if (getLogger().isDebugEnabled())
            getLogger().debug(
                         (paper.getImageableX()/72) * INCH_TO_MM
                         +", " +(paper.getImageableY()/72) * INCH_TO_MM
                         +", " +(paper.getImageableWidth() /72) * INCH_TO_MM
                         +", " +(paper.getImageableHeight() /72) * INCH_TO_MM
                         );
    }

    public String saveFile(Frame frame,String dir,final  String[] fileExtensions, String filename, byte[] content) throws IOException {
        final FileDialog fd = new FileDialog(frame, "Save File", FileDialog.SAVE);
        
        if ( dir == null)
        {
            try
            {
                dir = getDirectory();
            }   
            catch (Exception ex)
            {
                
            }
        }
        if ( dir != null)
        {
            fd.setDirectory(dir);
        }
        fd.setFile(filename);
        if ( fileExtensions.length > 0)
        {
            fd.setFilenameFilter( new FilenameFilter() {
                
                public boolean accept(File dir, String name) {
                    final String[] split = name.split(".");
                    if ( split.length > 1)
                    {
                        String extension = split[split.length -1].toLowerCase();
                        for ( String ext: fileExtensions)
                        {
                            if ( ext.toLowerCase().equals(extension ))
                            {
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });
        }
        fd.setLocation(50, 50);
        fd.setVisible( true);
        final String savedFileName = fd.getFile();

        if (savedFileName == null) {
            return null;
        }
        
        String path = createFullPath(fd);
        final File savedFile = new File( path);
        writeFile(savedFile, content);
        return path;
    }

    public FileContent openFile(Frame frame,String dir, String[] fileExtensions) throws IOException {
        final FileDialog fd = new FileDialog(frame, "Open File", FileDialog.LOAD);
        
        if ( dir == null)
        {
            dir = getDirectory();
        }
        fd.setDirectory(dir);
        fd.setLocation(50, 50);
        fd.setVisible( true);
        final String openFileName = fd.getFile();

        if (openFileName == null) {
            return null;
        }
        String path = createFullPath(fd);
        final FileInputStream openFile = new FileInputStream( path);
        FileContent content = new FileContent();
        content.setName( openFileName);
        content.setInputStream( openFile );
        return content;
    }

    
    private String getDirectory() {
        final String userHome = System.getProperty("user.home");

        if (userHome == null) {
            final File execDir = new File("");
            return execDir.getAbsolutePath();
        }

        return userHome;
    }

    private String createFullPath(final FileDialog fd) {
        return fd.getDirectory()
                + System.getProperty("file.separator").charAt(0) + fd.getFile();
    }

    private void writeFile(final File savedFile, byte[] content) throws IOException {
        final FileOutputStream out;
        out = new FileOutputStream(savedFile);
        out.write( content);
        out.flush();
        out.close();
    }

	public boolean openUrl(URL url) throws IOException {
		try
		{
			java.awt.Desktop.getDesktop().browse(url.toURI());
			return true;
		}
		catch (Throwable  ex)
		{
			getLogger().error(ex.getMessage(), ex);
			return false;
			
		}
	}

}




