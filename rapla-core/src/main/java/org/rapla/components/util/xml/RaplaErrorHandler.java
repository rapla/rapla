package org.rapla.components.util.xml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


public class RaplaErrorHandler implements ErrorHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaplaErrorHandler.class);

    public void error(SAXParseException exception) throws SAXException {
        throw exception;
    }

    public void fatalError(SAXParseException exception) throws SAXException {
        throw exception;
    }

    public void warning(SAXParseException exception) throws SAXException {
        LOGGER.error("Warning: {}", getString(exception));
    }

     public String getString(SAXParseException exception)  {
        return exception.getMessage();
    }
}
