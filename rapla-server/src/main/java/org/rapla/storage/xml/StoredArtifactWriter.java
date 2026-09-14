package org.rapla.storage.xml;

import org.rapla.entities.RaplaObject;
import org.rapla.entities.storage.StoredArtifact;
import org.rapla.framework.RaplaException;

import java.io.IOException;

public class StoredArtifactWriter extends RaplaXMLWriter
{
    public StoredArtifactWriter(RaplaXMLContext sm) throws RaplaException
    {
        super(sm);
    }

    public void printArtifact(StoredArtifact artifact) throws IOException, RaplaException
    {
        final String tagName = "rapla:artifact";
        openTag(tagName);
        printId(artifact);
        att("kind", artifact.getKind());
        att("name", artifact.getName());
        printOwner(artifact);
        printTimestamp(artifact);
        closeTag();
        {
            final String elementName = "body";
            openElementOnLine(elementName);
            printEncode(artifact.getBody());
            closeElementOnLine(elementName);
            println();
        }
        if (artifact.getMetadata() != null)
        {
            final String elementName = "metadata";
            openElementOnLine(elementName);
            printEncode(artifact.getMetadata());
            closeElementOnLine(elementName);
            println();
        }
        closeElement(tagName);
    }

    public void writeObject(RaplaObject object) throws IOException, RaplaException
    {
        printArtifact((StoredArtifact) object);
    }
}
