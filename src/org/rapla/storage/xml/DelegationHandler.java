/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
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

import java.util.Collection;
import java.util.HashSet;

import org.rapla.components.util.xml.RaplaSAXAttributes;
import org.rapla.components.util.xml.RaplaSAXHandler;
import org.rapla.components.util.xml.RaplaSAXParseException;
import org.xml.sax.SAXException;

class DelegationHandler implements RaplaSAXHandler
{
    StringBuffer currentText = null;

    DelegationHandler parent = null;
    DelegationHandler delegate = null;

    int level = 0;
    int entryLevel = 0;

    Collection<DelegationHandler> childHandlers;

    private void setParent( DelegationHandler parent )
    {
        this.parent = parent;
    }

    public void addChildHandler( DelegationHandler childHandler )
    {
        if (childHandlers == null)
            childHandlers = new HashSet<DelegationHandler>();
        childHandlers.add( childHandler );
        childHandler.setParent( this );
    }

	public void startElement(String namespaceURI, String localName,
			RaplaSAXAttributes atts) throws RaplaSAXParseException {
        //printToSystemErr( localName, atts );
        if (delegate != null)
        {
            delegate.startElement( namespaceURI, localName,  atts );
        }
        else
        {
            level++;
            processElement( namespaceURI, localName, atts );
        }
    }

//    protected void printToSystemErr( String localName, Attributes atts )
//    {
//        int len = atts.getLength();
//        StringBuffer buf = new StringBuffer();
//        for ( int i = 0; i<len;i++)
//        {
//            buf.append( " ");
//             buf.append( atts.getLocalName( i ));
//            buf.append( "=");
//            buf.append( atts.getValue( i ));
//            
//        }
//        System.err.println(localName + buf.toString());
//    }

    final public void endElement(
        String namespaceURI,
        String localName
        ) throws RaplaSAXParseException
    {
        if (delegate != null)
        {
            delegate.endElement( namespaceURI, localName);
            //After this call the delegate can be null again.
        }

        if (delegate == null)
        {
        	processEnd( namespaceURI, localName );
        	//Check if end of delegation reached
            if (entryLevel == level && parent != null)
            {
                parent.stopDelegation();
            }
            level--;
        }
    }

    final public void characters( char[] ch, int start, int length )
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
     * @param atts 
     * @throws RaplaSAXParseException 
     */
    public void processElement(
        String namespaceURI,
        String localName,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
    }

    /**
     * @param namespaceURI  
     * @param localName 
     * @throws RaplaSAXParseException 
     */
    public void processEnd( String namespaceURI, String localName )
        throws RaplaSAXParseException        
    {
    }

    /* Call this method to delegate the processessing of the encountered element with
     all its subelements to another DelegationHandler.
     */
    public final void delegateElement(
        DelegationHandler child,
        String namespaceURI,
        String localName,
        RaplaSAXAttributes atts ) throws RaplaSAXParseException
    {
        //System.out.println("Start delegation for " + localName);
        delegate = child;
        delegate.setDelegateLevel( level );
        delegate.processElement( namespaceURI, localName, atts );
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
        String result = currentText.toString();
        currentText = null;
        return result;
    }

    public RaplaSAXParseException createSAXParseException( String message )
    {
    	return createSAXParseException( message, null);
    }

    public RaplaSAXParseException createSAXParseException( String message,Exception cause )
    {
    	return new RaplaSAXParseException( message, cause);
    }


    /**
     * @throws SAXException  
     */
    public void processCharacters( char ch[], int start, int length )
    {
        if (currentText != null)
            currentText.append( ch, start, length );
    }


}
