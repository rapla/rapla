package org.rapla.storage.xml;

import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class ReferenceReader extends RaplaXMLReader
{
    public ReferenceReader( RaplaContext context ) throws RaplaException
    {
        super( context );
    }

    public void processElement(String namespaceURI,String localName,String qName,Attributes atts)
    throws SAXException
    {
        String id = atts.getValue("idref");
        if ( id!=null) {
            reference( localName, id);
        }
    }
}
