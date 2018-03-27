package org.rapla.framework.internal;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXHandler;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.framework.Configuration;
import org.rapla.framework.DefaultConfiguration;

import java.util.Map;
import java.util.Stack;

public class SAXConfigurationHandler implements RaplaSAXHandler 
{
    final Stack<DefaultConfiguration> configStack = new Stack<>();
    Configuration parsedConfiguration;
    
    public Configuration getConfiguration()
    {
        return parsedConfiguration;
    }

	public void startElement(String namespaceURI, String localName,
			RaplaSAXAttributes atts) throws RaplaSAXParseException
	{
		DefaultConfiguration defaultConfiguration = new DefaultConfiguration(localName);
        for ( Map.Entry<String,String> entry :atts.getMap().entrySet())
        {
            String name = entry.getKey();
            String value = entry.getValue();
            defaultConfiguration.setAttribute( name, value);
        }
        configStack.push( defaultConfiguration);
	}

	public void endElement(
	        String namespaceURI,
	        String localName
	        ) throws RaplaSAXParseException
	{
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

    
    public void characters(char[] ch, int start, int length) {
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
