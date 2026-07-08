package org.rapla.storage.xml;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.rapla.entities.storage.internal.StoredArtifactImpl;
import org.rapla.framework.RaplaException;

public class StoredArtifactReader extends RaplaXMLReader
{
    private StoredArtifactImpl artifact;

    public StoredArtifactReader(RaplaXMLContext context) throws RaplaException
    {
        super(context);
    }

    @Override
    public void processElement(String namespaceURI, String localName, RaplaSAXAttributes atts) throws RaplaSAXParseException
    {
        if (localName.equals("artifact"))
        {
            artifact = new StoredArtifactImpl();
            artifact.setId(getString(atts, "id"));
            artifact.setKind(getString(atts, "kind"));
            artifact.setName(getString(atts, "name"));
            setOwner(artifact, atts);
            setLastChangedBy(artifact, atts);
            final TimestampDates timestamps = readTimestamps(atts);
            artifact.setCreateDate(timestamps.createTime);
            artifact.setLastChanged(timestamps.changeTime);
        }
        else if (localName.equals("body") || localName.equals("metadata"))
        {
            startContent();
        }
    }

    @Override
    public void processEnd(String namespaceURI, String localName) throws RaplaSAXParseException
    {
        if (localName.equals("body"))
        {
            artifact.setBody(readContent());
        }
        else if (localName.equals("metadata"))
        {
            artifact.setMetadata(readContent());
        }
        else if (localName.equals("artifact"))
        {
            add(artifact);
            artifact = null;
        }
    }
}
