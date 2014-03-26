/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/
package org.rapla.gui.internal.view;
import java.awt.Dimension;
import java.awt.Point;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.rapla.components.util.Assert;
import org.rapla.entities.RaplaObject;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.toolkit.HTMLView;
import org.rapla.gui.toolkit.RaplaWidget;

/**Information of the entity-classes displayed in an HTML-Component */
public class ViewTable<T> extends RaplaGUIComponent
    implements
        HyperlinkListener
        ,RaplaWidget
        ,LinkController
{
    String title;
    HTMLView htmlView = new HTMLView();
    JScrollPane pane = new JScrollPane(htmlView, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {
        private static final long serialVersionUID = 1L;
        
        public Dimension getPreferredSize() {
            Dimension pref = super.getPreferredSize();
            Dimension max = getMaximumSize();
            //System.out.println( "PREF: " + pref + "  MAX: " + max);
            if  ( pref.height > max.height )
                return max;
            else
                return pref;
        }
    };
    Map<Integer,Object> linkMap;
    int linkId = 0;
    boolean packText = true;

    public ViewTable(RaplaContext sm) {
        super( sm);
        linkMap = new HashMap<Integer,Object>(7);
        htmlView.addHyperlinkListener(this);
        htmlView.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        pane.setMaximumSize( new Dimension( 600, 500 ));
    }

    /** HTML-text-component should be sized according to the displayed text. Default is true. */
    public void setPackText(boolean packText) {
        this.packText = packText;
    }

    public JComponent getComponent() {
        return pane;
    }

    public String getDialogTitle() {
        return title;
    }

    public void updateInfo(T object) throws RaplaException 
    {
        if ( object instanceof RaplaObject)
        {
            final InfoFactoryImpl infoFactory = (InfoFactoryImpl)getInfoFactory();
            @SuppressWarnings("unchecked")
			HTMLInfo<RaplaObject<T>> createView = infoFactory.createView((RaplaObject<T>)object);
			@SuppressWarnings("unchecked")
			final HTMLInfo<T> view = (HTMLInfo<T>) createView;
            updateInfo(object,view);
        }
        else
        {
            updateInfoHtml( object.toString());
        }
    }

    public void updateInfo(T object, HTMLInfo<T> info) throws RaplaException {
        linkMap.clear();
        final String html = info.createHTMLAndFillLinks( object, this);
        setTitle (info.getTitle( object));
        updateInfoHtml(html);
     }
    
    public void updateInfoHtml( String html)  {
        if (html !=null ) {
            setText( html);
        } else {
            setText(getString("nothing_selected"));
            htmlView.revalidate();
            htmlView.repaint();
        }
        final JViewport viewport = pane.getViewport();
        SwingUtilities.invokeLater( new Runnable() {
            
            @Override
            public void run() {
                viewport.setViewPosition(new Point(0,0));
                
            }
        });
    }

    public void setTitle(String text) {
        this.title = text;
    }

    public void setText(String text) {
        String message = HTMLView.createHTMLPage(text);
        htmlView.setText(message, packText);
    }

    public void createLink(Object object,String link,StringBuffer buf) {
        linkMap.put(new Integer(linkId),object);
        buf.append("<A href=\"");
        buf.append(linkId++);
        buf.append("\">");
        HTMLInfo.encode(link,buf);
        buf.append("</A>");
    }

    public String createLink(Object object,String link) {
        StringBuffer buf = new StringBuffer();
        createLink(object,link,buf);
        return  buf.toString();
    }

    public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
        	if ( true)
        	{
        	
        	}
        	String link = e.getDescription();
        	
        	try
        	{
        		Integer index=  Integer.parseInt(link);
        		getLogger().debug("Hyperlink pressed: " + link);
        		Object object = linkMap.get(index);
        		Assert.notNull(object,"link was not found in linkMap");
        		Assert.notNull(getInfoFactory());
        		try {
        			getInfoFactory().showInfoDialog(object,htmlView);
        		} catch (RaplaException ex) {
        			showException(ex,getComponent());
        		} // end of try-catch
        	}
        	catch ( NumberFormatException ex)
        	{
        		try 
        		{
					getIOService().openUrl(new URL(link));
				} 
        		catch (Exception e1) 
        		{
        			showException(ex,getComponent());
        		}	
        	}
        }
    }
}

