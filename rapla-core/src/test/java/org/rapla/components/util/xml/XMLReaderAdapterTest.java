package org.rapla.components.util.xml;

import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A9 (security): the shared SAX parser must reject DOCTYPE declarations, so an
 * XML dataset / DB blob cannot smuggle an external entity (XXE: file read / SSRF)
 * or an internal entity bomb (billion laughs). rapla's own XML never uses a DOCTYPE.
 */
class XMLReaderAdapterTest
{
    private static void parse(String xml) throws Exception
    {
        XMLReader reader = XMLReaderAdapter.createXMLReader(false);
        reader.setContentHandler(new DefaultHandler());
        reader.parse(new InputSource(new StringReader(xml)));
    }

    @Test
    void doctypeWithInternalEntityIsRejected()
    {
        // Without disallow-doctype-decl this parses and expands &x; to "expanded".
        assertThrows(SAXException.class,
                () -> parse("<!DOCTYPE r [<!ENTITY x \"expanded\">]><r>&x;</r>"));
    }

    @Test
    void doctypeWithExternalEntityIsRejected()
    {
        assertThrows(SAXException.class,
                () -> parse("<!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><r>&x;</r>"));
    }

    @Test
    void plainXmlWithoutDoctypeStillParses() throws Exception
    {
        // hardening must not break normal (DOCTYPE-free) rapla XML
        parse("<r><child a=\"1\">text</child></r>");
    }
}
