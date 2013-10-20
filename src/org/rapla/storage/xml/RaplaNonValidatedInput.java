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
package org.rapla.storage.xml;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.rapla.components.util.xml.XMLReaderAdapter;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/** Reads the data in xml format from an InputSource into the
    LocalCache and converts it to a newer version if necessary.
 */
public final class RaplaNonValidatedInput {
    private Logger logger;

    public RaplaNonValidatedInput(Logger logger) {
        this.logger = logger;
    }

    protected Logger getLogger() {
        return logger;
    }

    public void read(Reader xmlReader, ContentHandler handler) throws RaplaException,IOException {
        parseData( handler, new InputSource( xmlReader));
    }
    
    public void readWithNamespaces(String xml, ContentHandler handler) throws RaplaException,IOException {
        StringBuffer dataElement = new StringBuffer();
        dataElement.append("<rapla:data ");
        for (int i=0;i<RaplaXMLWriter.NAMESPACE_ARRAY.length;i++) {
            String prefix = RaplaXMLWriter.NAMESPACE_ARRAY[i][1];
            String uri = RaplaXMLWriter.NAMESPACE_ARRAY[i][0];
            if ( prefix == null) {
                dataElement.append("xmlns=");
            } else {
               dataElement.append("xmlns:" + prefix + "=");
            }
            dataElement.append("\"");
            dataElement.append( uri );
            dataElement.append("\" ");
        }
        dataElement.append(">");
        String xmlWithNamespaces = dataElement.toString() + xml + "</rapla:data>"; 
        read(new StringReader(xmlWithNamespaces), handler);
    }

    
    private void parseData( ContentHandler contentHandler, InputSource source)
        throws RaplaException
               ,IOException {
        try {
        	XMLReader reader = XMLReaderAdapter.createXMLReader(false);
        	reader.setContentHandler( contentHandler);
        	reader.parse(source );
        	reader.setErrorHandler( new RaplaErrorHandler( logger));
        } catch (SAXException ex) {
            Throwable cause = ex.getException();
            if (cause instanceof SAXParseException) {
                ex = (SAXParseException) cause;
                cause = ex.getException();
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
    }
    

}
