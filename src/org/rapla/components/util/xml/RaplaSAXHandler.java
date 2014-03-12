package org.rapla.components.util.xml;


public interface RaplaSAXHandler {
	public void startElement(String namespaceURI, String localName,
			RaplaSAXAttributes atts) throws RaplaSAXParseException;
	
	public void endElement(
		        String namespaceURI,
		        String localName
		        ) throws RaplaSAXParseException;
	
	public void characters( char[] ch, int start, int length );
}
