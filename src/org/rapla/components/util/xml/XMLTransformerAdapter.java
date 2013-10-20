/*---------------------------------------------------------------------------*
  | (C) 2006 Christopher Kohlhaas                                            |
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
package org.rapla.components.util.xml;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.xml.sax.SAXException;

final public class XMLTransformerAdapter {
    /** Here you can set the xslt-transformer-factory implementation that should be used if
        TransformerFactory.newInstance() fails. The default implementation
        is the saxon transformer-factory from the saxon project: net.sf.saxon.TransformerFactoryImpl
    */
    public static String XSLT_TRANSFORMER_FACTORY_IMPL = "com.icl.saxon.TransformerFactoryImpl";

    private static ClassLoader getClassLoader() {
        return XMLTransformerAdapter.class.getClassLoader();
    }

    public static void checkXMLSupport() throws ClassNotFoundException {
        try {
            getClassLoader().loadClass("javax.xml.transform.sax.SAXTransformerFactory");
        } catch (ClassNotFoundException ex) {
            throw new ClassNotFoundException
                ("Couldn't find Transformer-API: javax.xml.transform"
                 + " You need java 1.4 or higher. For java-versions below 1.4 please download"
                 + " the saxon.jar from rapla.sourceforge.net"
                 + " and put it into the lib directory.");
        }
    }

    public static SAXTransformerFactory getTransformerFactory() throws SAXException{
        try {
            return (SAXTransformerFactory) TransformerFactory.newInstance();
        } catch (TransformerFactoryConfigurationError ex) {
            System.err.println("Couldn't initialize default SAXTransformerFactory. Now trying '"
                        + XSLT_TRANSFORMER_FACTORY_IMPL + "'");
            try {
                getClassLoader().loadClass("javax.xml.parsers.SAXParserFactory");
                return (SAXTransformerFactory) getClassLoader().loadClass(XSLT_TRANSFORMER_FACTORY_IMPL).newInstance();
            } catch (ClassNotFoundException ex2) {
                throw new SAXException("Couldn't find '" + XSLT_TRANSFORMER_FACTORY_IMPL
                                       +"' on classpath. Requiered library is missing!");
            } catch (ClassCastException ex2) {
                throw new SAXException("Wrong class: " + XSLT_TRANSFORMER_FACTORY_IMPL
                                       + " doesnt implement SAXTransformerFactory");
            } catch (Exception ex2) {
                throw new SAXException("Couldn't load SAXTransformerFactory '"
                                       + XSLT_TRANSFORMER_FACTORY_IMPL +"' : " + ex2.getMessage());
            }
        }
    }

}
