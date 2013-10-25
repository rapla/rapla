package org.rapla.storage.xml;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class ReferenceReader extends RaplaXMLReader
{
    public ReferenceReader( RaplaContext context ) throws RaplaException
    {
        super( context );
    }

    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
    throws RaplaSAXParseException
    {
        String id = atts.getValue("idref");
        if ( id!=null) {
            reference( localName, id);
        }
    }
}
