/*--------------------------------------------------------------------------*
  | Copyright (C) 2014 Christopher Kohlhaas                                  |
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

import org.rapla.components.util.xml.RaplaContentHandler;
import org.rapla.components.util.xml.RaplaErrorHandler;
import org.rapla.components.util.xml.RaplaSAXHandler;
import org.rapla.components.util.xml.XMLReaderAdapter;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;

/** Reads the data in xml format from an InputSource into the
    LocalCache and converts it to a newer version if necessary.
 */
public final class RaplaInput {
    private Logger logger;
    private URL fileSource;
    private Reader reader;
    
    public RaplaInput(Logger logger) {
        this.logger = logger;
    }

    protected Logger getLogger() {
        return logger;
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
            InputSource source = getNewSource();
            if (validate) {
                validate( source, "org/rapla/storage/xml/rapla.rng"); 
            } 

            XMLReader parser = XMLReaderAdapter.createXMLReader(false);
            RaplaErrorHandler errorHandler = new RaplaErrorHandler(logger);
            parser.setContentHandler(contentHandler);
            parser.setErrorHandler(errorHandler);
            parser.parse(source);
        } catch (SAXException ex) {
            Throwable cause = ex.getCause();
            while (cause != null && cause.getCause() != null) {
                cause = cause.getCause();
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
            Method put = propMapBuilderC.getMethod("put", propIdC, Object.class);
            put.invoke( propMapBuilder, errorHandlerId, errorHandler);
            Method topropMap = propMapBuilderC.getMethod("toPropertyMap");
            Object propMap = topropMap.invoke( propMapBuilder);
            Constructor<?> validatorConst = validatorC.getConstructor(propMapC);
            Object validator = validatorConst.newInstance(propMap);
            Method loadSchema = validatorC.getMethod( "loadSchema", InputSource.class);
            Method validate = validatorC.getMethod("validate", InputSource.class);
            InputSource schemaSource = new InputSource( getResource( schema ).toString() );
            loadSchema.invoke( validator, schemaSource);
            validate.invoke( validator, in);
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

}
