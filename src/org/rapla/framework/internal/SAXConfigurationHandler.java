package org.rapla.framework.internal;

import java.util.Stack;

import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class SAXConfigurationHandler extends DefaultHandler
{
    final Stack<DefaultConfiguration> configStack = new Stack<DefaultConfiguration>();
    Configuration parsedConfiguration;
    
    public Configuration getConfiguration()
    {
        return parsedConfiguration;
    }

    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        DefaultConfiguration defaultConfiguration = new DefaultConfiguration(localName);
        for ( int i=0;i<atts.getLength();i++)
        {
            String name = atts.getLocalName( i);
            String value = atts.getValue( i);
            defaultConfiguration.setAttribute( name, value);
        }
        configStack.push( defaultConfiguration);
    }

    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        DefaultConfiguration  current =configStack.pop();
        if ( configStack.isEmpty())
        {
            parsedConfiguration = current;
        }
        else
        {
            DefaultConfiguration parent = configStack.peek();
            parent.addChild( current);
        }
    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        DefaultConfiguration peek = configStack.peek();
        String value = peek.getValue(null);
        StringBuffer buf = new StringBuffer();
        if ( value != null)
        {
            buf.append( value);
        }
        buf.append( ch, start, length);
        String string = buf.toString();
        if ( string.trim().length() > 0)
        {
            peek.setValue( string);
        }
    }

}
