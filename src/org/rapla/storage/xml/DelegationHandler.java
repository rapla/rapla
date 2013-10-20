/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org .       |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.storage.xml;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

class DelegationHandler implements ContentHandler
{
    StringBuffer currentText = null;

    DelegationHandler parent = null;
    DelegationHandler delegate = null;

    int level = 0;
    int entryLevel = 0;
    Locator locator;

    Collection<DelegationHandler> childHandlers;

    public void setDocumentLocator( Locator locator )
    {
        this.locator = locator;
        if (childHandlers == null)
            return;

        Iterator<DelegationHandler> it = childHandlers.iterator();
        while (it.hasNext())
        {
            ( it.next()).setDocumentLocator( locator );
        }
    }

    private void setParent( DelegationHandler parent )
    {
        this.parent = parent;
    }

    protected Locator getLocator()
    {
        return locator;
    }

    public void addChildHandler( DelegationHandler childHandler )
    {
        if (childHandlers == null)
            childHandlers = new HashSet<DelegationHandler>();
        childHandlers.add( childHandler );
        childHandler.setParent( this );
    }

    public final void startDocument()
    {
        this.level = 0;
        this.entryLevel = 0;
    }

    public final void endDocument() throws SAXException
    {
        if (parent != null)
            throw new SAXException( "Unexpected end of Document" );
    }

    final public void startElement(
        String namespaceURI,
        String localName,
        String qName,
        Attributes atts ) throws SAXException
    {
        try
        {
            //printToSystemErr( localName, atts );
            if (delegate != null)
            {
                delegate.startElement( namespaceURI, localName, qName, atts );
            }
            else
            {
                level++;
                processElement( namespaceURI, localName, qName, atts );
            }
        }
        catch (SAXException ex)
        {
            throw ex;
        }
        catch (Exception ex)
        {
            throw new SAXException( ex );
        }
    }

    protected void printToSystemErr( String localName, Attributes atts )
    {
        int len = atts.getLength();
        StringBuffer buf = new StringBuffer();
        for ( int i = 0; i<len;i++)
        {
            buf.append( " ");
             buf.append( atts.getLocalName( i ));
            buf.append( "=");
            buf.append( atts.getValue( i ));
            
        }
        System.err.println(localName + buf.toString());
    }

    final public void endElement(
        String namespaceURI,
        String localName,
        String qName ) throws SAXException
    {
        if (delegate != null)
        {
            delegate.endElement( namespaceURI, localName, qName );
            //After this call the delegate can be null again.
        }

        if (delegate == null)
        {
            processEnd( namespaceURI, localName, qName );
            //Check if end of delegation reached
            if (entryLevel == level && parent != null)
            {
                parent.stopDelegation();
            }
            level--;
        }
    }

    public void startPrefixMapping( String prefix, String uri )
        throws SAXException
    {
        if (delegate != null)
        {
            delegate.startPrefixMapping( prefix, uri );
        }
    }

    public void skippedEntity( String name ) throws SAXException
    {
        if (delegate != null)
        {
            delegate.skippedEntity( name );
        }
    }

    public void endPrefixMapping( String prefix ) throws SAXException
    {
        if (delegate != null)
        {
            delegate.endPrefixMapping( prefix );
        }
    }

    public void ignorableWhitespace( char[] ch, int start, int length )
        throws SAXException
    {
        if (delegate != null)
        {
            delegate.ignorableWhitespace( ch, start, length );
        }
    }

    public void processingInstruction( String target, String data )
        throws SAXException
    {
        if (delegate != null)
        {
            delegate.processingInstruction( target, data );
        }
    }

    final public void characters( char[] ch, int start, int length )
        throws SAXException
    {
        if (delegate != null)
        {
            delegate.characters( ch, start, length );
        }
        else
        {
            processCharacters( ch, start, length );
        }
    }

    /**
     * @param namespaceURI  
     * @param localName 
     * @param qName 
     * @param atts 
     * @throws SAXException 
     */
    public void processElement(
        String namespaceURI,
        String localName,
        String qName,
        Attributes atts ) throws SAXException
    {
    }

    /**
     * @param namespaceURI  
     * @param localName 
     * @param qName 
     * @throws SAXException 
     */
    public void processEnd( String namespaceURI, String localName, String qName )
        throws SAXException
    {
    }

    /* Call this method to delegate the processessing of the encountered element with
     all its subelements to another DelegationHandler.
     */
    public final void delegateElement(
        DelegationHandler child,
        String namespaceURI,
        String localName,
        String qName,
        Attributes atts ) throws SAXException
    {
        //System.out.println("Start delegation for " + localName);
        delegate = child;
        delegate.setDelegateLevel( level );
        delegate.processElement( namespaceURI, localName, qName, atts );
    }

    private void stopDelegation()
    {
        delegate = null;
    }

    private void setDelegateLevel( int level )
    {
        this.entryLevel = level;
        this.level = level;
    }

    public void startContent()
    {
        currentText = new StringBuffer();
    }

    public String readContent()
    {
        if (currentText == null)
            return null;
        String result = currentText.toString().trim();
        currentText = null;
        return result;
    }

    public SAXParseException createSAXParseException( String message )
    {
    	return createSAXParseException( message, null);
    }

    public SAXParseException createSAXParseException( String message,Exception cause )
    {
        // This method resolves a bug with crimson. An EmtpyStacTraceException is 
        // thrown when you create a SAXParseException with a Locator
        SAXParseException ex;
        try
        {
            if ( cause != null)
            {
            	ex = new SAXParseException( message, getLocator(), cause );
            }
            else
            {
            	ex = new SAXParseException( message, getLocator() );
            }
        }
        catch (Exception e)
        {
            ex = new SAXParseException( message, null );
        }
        return ex;
    }

    public SAXParseException createSAXParseException( Exception ex )
    {
        String message = ex.getMessage();
        if (message == null || message.length() == 0)
        {
            StringWriter writer = new StringWriter();
            PrintWriter print = new PrintWriter( writer );
            ex.printStackTrace( print );
            message = writer.toString();
        }
        return createSAXParseException( message );
    }

    /**
     * @throws SAXException  
     */
    public void processCharacters( char ch[], int start, int length )
        throws SAXException
    {
        if (currentText != null)
            currentText.append( ch, start, length );
    }

}
