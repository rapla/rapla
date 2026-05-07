package org.rapla.components.util.xml;

import org.rapla.logger.Logger;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;


public class RaplaErrorHandler implements ErrorHandler {
    Logger logger;
    
    public RaplaErrorHandler(Logger logger) {
        this.logger = logger;
    }

    public void error(SAXParseException exception) throws SAXException {
        throw exception;
    }

    public void fatalError(SAXParseException exception) throws SAXException {
        throw exception;
    }

    public void warning(SAXParseException exception) throws SAXException {
        if (logger != null)
            logger.error("Warning: " + getString(exception));
    }

     public String getString(SAXParseException exception)  {
        //       return "Line " + exception.getLineNumber()
        //      +    "\t Col  " + exception.getColumnNumber()
        //      +    "\t " +
        return exception.getMessage();
    }
}