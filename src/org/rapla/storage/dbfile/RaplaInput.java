/*--------------------------------------------------------------------------*
  | Copyright (C) 2006 Christopher Kohlhaas                                  |
  |                                                                          |
  | This program is free software; you can redistribute it and/or modify     |
  | it under the terms of the GNU General Public License as published by the |
  | Free Software Foundation. A copy of the license has been included with   |
  | these distribution in the COPYING file, if not go to www.fsf.org .       |
  |                                                                          |
  | As a special exception, you are granted the permissions to link this     |
  | program with every library, which license fulfills the Open Source       |
  | Definition as published by the Open Source Initiative (OSI).             |
  *--------------------------------------------------------------------------*/
package org.rapla.storage.dbfile;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

import org.rapla.components.util.xml.RaplaContentHandler;
import org.rapla.components.util.xml.RaplaErrorHandler;
import org.rapla.components.util.xml.RaplaSAXHandler;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.storage.xml.RaplaMainReader;
import org.rapla.storage.xml.WrongXMLVersionException;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/** Reads the data in xml format from an InputSource into the
    LocalCache and converts it to a newer version if necessary.
 */
public final class RaplaInput {
    private Logger logger;
    private URL fileSource;
    private Reader reader;
    
    private boolean wasConverted;

    public RaplaInput(Logger logger) {
        this.logger = logger;
    }

    protected Logger getLogger() {
        return logger;
    }

    /** returns if the data was converted during read.*/
    public boolean wasConverted() {
        return wasConverted;
    }

    public void read(URL file, RaplaSAXHandler handler, boolean validate) throws RaplaException,IOException {
        getLogger().debug("Parsing " + file.toString());
        fileSource = file;
        reader = null;
        parseData( handler , validate);
    }


    private InputSource getNewSource() {
        if ( fileSource != null ) {
            return new InputSource( fileSource.toString() );
        } else if ( reader != null ) {
            return new InputSource( reader  );
        } else {
            throw new IllegalStateException("fileSource or reader can't be null");
        }
    }
    
    private void parseData( RaplaSAXHandler reader,boolean validate)
        throws RaplaException
               ,IOException {
    	ContentHandler contentHandler = new RaplaContentHandler( reader);
        try {
            RaplaSAXPipeline pipeline = new RaplaSAXPipeline(getLogger());
            if (validate) {
                validate( getNewSource(), "org/rapla/storage/xml/rapla.rng"); 
            } 
            pipeline.parse( contentHandler, getNewSource() );
        } catch (SAXException ex) {
            Throwable cause = ex.getCause();
            while (cause != null && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof WrongXMLVersionException) {
                convertData( getNewSource(),contentHandler,((WrongXMLVersionException)cause).getVersion());
                return;
            }
            if (ex instanceof SAXParseException) {
                throw new RaplaException("Line: " + ((SAXParseException)ex).getLineNumber()
                                         + " Column: "+ ((SAXParseException)ex).getColumnNumber() + " "
                                         +  ((cause != null) ? cause.getMessage() : ex.getMessage())
                                         ,(cause != null) ? cause : ex );
            }
            if (cause == null) {
                throw new RaplaException( ex);
            }
            if (cause instanceof RaplaException)
                throw (RaplaException) cause;
            else
                throw new RaplaException( cause);
        }
        /*  End of Exception Handling */
    }
    
    /** uses the jing validator to validate a document against an relaxng schema.
     * This method uses reflection API, to avoid compile-time dependencies on 
     * the jing.jar
     * @param in
     * @param schema
     * @throws RaplaException
     */
    private void validate(InputSource in, String schema) throws RaplaException {
        try {
            ErrorHandler errorHandler = new RaplaErrorHandler(getLogger());
            /* // short version 
             * propMapBuilder = new com.thaiopensource.util.PropertyMapBuilder();
             * propMapBuilder.put(com.thaiopensource.validate.ValidateProperty.ERROR_HANDLER, errorHandler);
             * Object propMap = propMapBuilder.toPropertyMap();
             * Object o =new com.thaiopensource.validate.ValidationDriver(propMap);
             * o.loadSchema(schema);
             * o.validate(in);
             */
            // full reflection syntax
            Class<?> validatorC = Class.forName("com.thaiopensource.validate.ValidationDriver");
            Class<?> propIdC = Class.forName("com.thaiopensource.util.PropertyId");
            Class<?> validatepropC = Class.forName("com.thaiopensource.validate.ValidateProperty");
            Object errorHandlerId = validatepropC.getDeclaredField("ERROR_HANDLER").get( null );
            Class<?> propMapC = Class.forName("com.thaiopensource.util.PropertyMap");
            Class<?> propMapBuilderC = Class.forName("com.thaiopensource.util.PropertyMapBuilder");
            Object propMapBuilder = propMapBuilderC.newInstance();
            Method put = propMapBuilderC.getMethod("put", new Class[] {propIdC, Object.class} );
            put.invoke( propMapBuilder, new Object[] {errorHandlerId, errorHandler});
            Method topropMap = propMapBuilderC.getMethod("toPropertyMap", new Class[] {} );
            Object propMap = topropMap.invoke( propMapBuilder, new Object[] {});
            Constructor<?> validatorConst = validatorC.getConstructor( new Class[] { propMapC });
            Object validator = validatorConst.newInstance( new Object[] {propMap});
            Method loadSchema = validatorC.getMethod( "loadSchema", new Class[] {InputSource.class});
            Method validate = validatorC.getMethod("validate", new Class[] {InputSource.class});
            InputSource schemaSource = new InputSource( getResource( schema ).toString() );
            loadSchema.invoke( validator, new Object[] {schemaSource} );
            validate.invoke( validator, new Object[] {in});
        } catch (ClassNotFoundException ex) {
            throw new RaplaException( ex.getMessage() + ". Latest jing.jar is missing on the classpath. Please download from http://www.thaiopensource.com/relaxng/jing.html");
        } catch (InvocationTargetException e) {
            throw new RaplaException("Can't validate data due to the following error: " + e.getTargetException().getMessage(), e.getTargetException());
        } catch (Exception ex) {
            throw new RaplaException("Error invoking JING", ex);
        }
    }
    private URL getResource(String name) throws RaplaException {
        URL url = getClass().getClassLoader().getResource( name );
        if ( url == null )
            throw new RaplaException("Resource " + name + " not found");
        return url;
    }
    private void convertData(InputSource inputSource,ContentHandler handler,String versionString)
        throws RaplaException,IOException
    {
        double version;
        try {
            version = new Double(versionString).doubleValue();
        } catch (NumberFormatException ex) {
            throw new RaplaException("Invalid version tag (double-value expected)!");
        }
        // get the version number of the data-schema
        if (version > new Double(RaplaMainReader.INPUT_FILE_VERSION).doubleValue())
            throw new RaplaException("This version of Rapla cannot read files with a version-number"
                                     + " greater than " + RaplaMainReader.INPUT_FILE_VERSION
                                     + ", try out the latest version.");

        try {
            RaplaSAXPipeline pipeline = new RaplaSAXPipeline(getLogger());
            if (version < 0.4) {
                throw new RaplaException("Rapla 0.7, 0.6 or rapla 0.5 files are not supported in this version\n"
                                         + " Please use rapla version 0.8.2 to convert this file: Load file, edit and save something!");
            }
            if (version < 0.5) {
                URL stylesheet = getResource( "org/rapla/storage/dbfile/convert0_4to0_5.xsl" );
                pipeline.addTransformer(stylesheet,new String[][] {});
            }
            if (version < 0.6) {
                URL stylesheet = getResource( "org/rapla/storage/dbfile/convert0_5to0_6.xsl" );
                pipeline.addTransformer(stylesheet,new String[][] {});
            }
            if (version < 0.7) {
                URL stylesheet = getResource( "org/rapla/storage/dbfile/convert0_6to0_7.xsl" );
                pipeline.addTransformer(stylesheet,new String[][] {});
            }
            if (version < 0.8) {
                URL stylesheet = getResource( "org/rapla/storage/dbfile/convert0_7to0_8.xsl" );
                pipeline.addTransformer(stylesheet,new String[][] {});
            }
            if (version < 0.9) {
                URL stylesheet = getResource( "org/rapla/storage/dbfile/convert0_8to0_9.xsl" );
                pipeline.addTransformer(stylesheet,new String[][] {});
            }
            
            if (version < 1.0) {
                URL stylesheet = getResource( "org/rapla/storage/dbfile/convert0_9to1_0.xsl" );
                pipeline.addTransformer(stylesheet,new String[][] {});
            }

            getLogger().info("Start conversion");
            //pipeline.parse(new DefaultHandler(), inputSource);
            
            pipeline.parse(handler, inputSource);
            getLogger().info("Conversion successful");
            wasConverted = true;
        } catch (SAXException ex) {
            Throwable cause = ex.getException();
            if (cause == null)
                throw new RaplaException( ex);

            if (cause instanceof RaplaException) {
                throw (RaplaException)cause;
            } else {
                throw new RaplaException( cause );
            }
        }
    }

}
