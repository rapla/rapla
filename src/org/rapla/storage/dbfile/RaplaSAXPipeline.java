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
import java.net.URL;
import java.util.Vector;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.rapla.components.util.Assert;
import org.rapla.components.util.xml.RaplaErrorHandler;
import org.rapla.components.util.xml.XMLReaderAdapter;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

class RaplaSAXPipeline  {
    Vector<XMLFilter> filters = new Vector<XMLFilter>();
    String factoryName;
    XMLFilter mainFilter;
    RaplaErrorHandler errorHandler;
    String xmlParser;

    SAXTransformerFactory stf;
    XMLReader stylesheetReader;

    Logger logger = null;

    RaplaSAXPipeline(Logger logger) {
        mainFilter = new XMLFilterImpl();
        this.logger = logger;
        errorHandler = new RaplaErrorHandler(logger);
    }

    private Transformer createTransformer(InputSource in) throws RaplaException,SAXException {
        SAXTransformerFactory stf = XMLTransformerAdapter.getTransformerFactory();
        try {
            if (stylesheetReader == null) {
                stylesheetReader = XMLReaderAdapter.createXMLReader(false);
            }
            return stf.newTransformer(new SAXSource(stylesheetReader,in));
        } catch (TransformerConfigurationException ex) {
            throw new RaplaException(ex);
        }
    }

    public void addTransformer(URL file)
        throws RaplaException,SAXException
    {
        addTransformer(file,new String[0][2]);
    }

    public void addTransformer(URL file,String[][] parameter)
        throws RaplaException,SAXException
    {
        if (logger != null )
            logger.info("Creating new transformer with stylesheet '" + file + "'");
        Transformer transformer = createTransformer(new InputSource(file.toString()));
        for (int i=0;i<parameter.length;i++) {
            transformer.setParameter(parameter[i][0],parameter[i][1]);
        }
        XMLFilter f = new TransformerFilter(transformer);
        filters.add(f);
        if (logger != null && logger.isDebugEnabled())
            logger.debug("adding transformer '" + file + "'");
    }

    public void parse(ContentHandler handler,InputSource source)
        throws IOException
               ,SAXException
    {
        XMLReader reader = XMLReaderAdapter.createXMLReader(false);
        
        // filter1 will use the SAX parser as it's reader.
        XMLReader lastFilter = reader;
        for (XMLFilter filter: filters) {
            filter.setParent(lastFilter);
            lastFilter = filter;
        }
        

        mainFilter.setParent(lastFilter);
        mainFilter.setContentHandler(handler);
        mainFilter.setErrorHandler(errorHandler);

        // Now, when you call the MainFilter to parse, it will set
        // itself as the ContentHandler for the previous filter, and
        // call the parse method on this filter, which will set itself as the
        // content handler for its previos filter, ...
        // The first filter will set itself as the content listener for the
        // SAX parser, and call parser.parse(new InputSource(foo_xml)).
        mainFilter.parse(source);
    }
    
}


class TransformerFilter extends XMLFilterImpl {
    Transformer transformer;
    
    public TransformerFilter(Transformer transformer) {
        this.transformer = transformer;
    }

    public void parse (InputSource input) throws IOException, SAXException {
        XMLReader parser = getParent();
        Assert.notNull(parser,"Must call setParent first");
        SAXSource source = new SAXSource();
        source.setInputSource(input);
        source.setXMLReader(parser);
        SAXResult result = new SAXResult();
        result.setHandler(getContentHandler());
        try {
            transformer.transform(source, result);
        } catch (TransformerException err) {
            Throwable cause = err.getException();
            if (cause != null && cause instanceof SAXException) {
                throw (SAXException)cause;
            } else if (cause != null && cause instanceof IOException) {
                throw (IOException)cause;
            } else {
                throw new SAXException(err);
            }
        }
    }
}
