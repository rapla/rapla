package org.rapla.components.util.xml;


public interface RaplaSAXHandler {
	void startElement(String namespaceURI, String localName, RaplaSAXAttributes atts) throws RaplaSAXParseException;
	
	void endElement(String namespaceURI, String localName) throws RaplaSAXParseException;
	
	void characters(char[] ch, int start, int length);
}
