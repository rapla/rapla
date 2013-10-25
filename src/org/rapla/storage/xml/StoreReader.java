package org.rapla.storage.xml;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;

public class StoreReader extends RaplaXMLReader
{
    public StoreReader( RaplaContext context ) throws RaplaException
    {
        super( context );
    }

    @Override
    public void processElement(String namespaceURI,String localName,RaplaSAXAttributes atts)
    throws RaplaSAXParseException
    {
        String id = atts.getValue("idref");
        if ( id!=null) {
            store( localName, id);
        }
    }

}
