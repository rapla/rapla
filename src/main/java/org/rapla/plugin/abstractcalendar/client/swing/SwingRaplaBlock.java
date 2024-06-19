/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender, Christopher Kohlhaas               |
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

package org.rapla.plugin.abstractcalendar.client.swing;

import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.toolkit.AWTColorUtil;
import org.rapla.components.calendarview.Block;
import org.rapla.components.calendarview.swing.SwingBlock;
import org.rapla.entities.Named;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.RequestStatus;
import org.rapla.entities.domain.Reservation;
import org.rapla.plugin.abstractcalendar.RaplaBlock;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.event.MouseInputListener;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TexturePaint;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SwingRaplaBlock extends RaplaBlock implements SwingBlock
{
    private static BufferedImage exceptionImage;
    RaplaBlockView m_view = new RaplaBlockView();

    public SwingRaplaBlock(RaplaBuilder.RaplaBlockContext blockContext, Date start, Date end)
    {
        super(blockContext, start, end);
    }

    public Icon getRepeatingIcon() {
        return RaplaImages.getIcon(getI18n().getIcon("icon.repeating"));
    }
    

    public ImageIcon getExceptionBackgroundIcon() {
        return RaplaImages.getIcon(getI18n().getIcon("icon.exceptionBackground"));
    }
    
    private BufferedImage getExceptionImage()
    {
        if ( exceptionImage != null )
            return exceptionImage;

        Image image = getExceptionBackgroundIcon().getImage();
        MediaTracker m = new MediaTracker( m_view );
        m.addImage( image, 0 );
        try
        {
            m.waitForID( 0 );
        }
        catch ( InterruptedException ex )
        {
        }

        exceptionImage = new BufferedImage( image.getWidth( null ), image.getHeight( null ),
                                            BufferedImage.TYPE_INT_ARGB );
        Graphics g = exceptionImage.getGraphics();
        g.drawImage( image, 0, 0, null );
        return exceptionImage;
    }

    public Component getView()
    {
        return m_view;
    }


    static Color TRANS = new Color( 100, 100, 100, 100 );

    public void paintDragging( Graphics g, int width, int height )
    {
        g.setColor( TRANS );
        m_view.paint( g, width, height );
        g.setColor( LINECOLOR_ACTIVE );
        g.drawRoundRect( 0, 0, width, height, 5, 5 );
    }

    static Font FONT_TITLE = new Font( "SansSerif", Font.BOLD, 12 );
    static Font FONT_SMALL_TITLE = new Font( "SansSerif", Font.BOLD, 10 );
    static Font FONT_INVISIBLE = new Font( "SansSerif", Font.PLAIN, 10 );
    static Font FONT_RESOURCE = new Font( "SansSerif", Font.PLAIN, 12 );
    static Font FONT_PERSON = new Font( "SansSerif", Font.ITALIC, 12 );
    static String FOREGROUND_COLOR = AWTColorUtil.getHexForColor( Color.black );

    static Map<Integer,Map<String,Color>> alphaMap = new HashMap<>();

    private static final Color LINECOLOR_INACTIVE = Color.darkGray;
    private static final Color LINECOLOR_ACTIVE = new Color( 255, 90, 10 );
    private static final Color LINECOLOR_SAME_RESERVATION = new Color( 180, 20, 120 );

    // The Linecolor is not static because it can be changed depending on the mouse move
    private Color linecolor = LINECOLOR_INACTIVE;

    class RaplaBlockView extends JComponent implements MouseInputListener
    {
        private static final long serialVersionUID = 1L;

        RaplaBlockView()
        {
            javax.swing.ToolTipManager.sharedInstance().registerComponent( this );
            addMouseListener( this );
        }

        public String getName( Named named )
        {
            return SwingRaplaBlock.this.getNameFor( named );
        }

        public String getName( Allocatable allocatable, Reservation reservation )
        {
            final RequestStatus requestStatus = reservation.getRequestStatus(allocatable);
            String name = this.getName(allocatable);
            if (RequestStatus.CHANGED == requestStatus || RequestStatus.REQUESTED == requestStatus) {
                name = "Anfrage:" + name;
            } else if (RequestStatus.DENIED == requestStatus ) {
                name = "Abgelehnt:" + name;
            }

            return name;
        }

        public String getToolTipText( MouseEvent evt )
        {
            String text = "";
            if ( getContext().isAnonymous() )
            {
                text = getI18n().getString( "not_visible.help" );
            }
            else if ( !getContext().isBlockSelected() && !getBuildContext().isConflictSelected() )
            {
                text = getI18n().getString( "not_selected.help" );
            }
            else
            {
                text = getContext().getTooltip( );
            }
            return "<html>" + text + "</html>";
        }

        private Color adjustColor( String org, int alpha )
        {
            Map<String,Color> colorMap =  alphaMap.get( alpha );
            if ( colorMap == null )
            {
                colorMap = new HashMap<>();
                alphaMap.put( alpha, colorMap );
            }
            Color color = colorMap.get( org );
            if ( color == null )
            {
                Color or;
                try
                {
                    or = AWTColorUtil.getColorForHex( org );
                }
                catch ( NumberFormatException nf )
                {
                    or = AWTColorUtil.getColorForHex( "#FFFFFF" );
                }
                color = new Color( or.getRed(), or.getGreen(), or.getBlue(), alpha );
                colorMap.put( org, color );
            }

            return color;
        }

        public void paint( Graphics g )
        {
            Dimension dim = getSize();
            paint( g, dim.width, dim.height );
        }

        public void paint( Graphics g, int width, int height )
        {
            int alpha = g.getColor().getAlpha();

            if ( !getContext().isBlockSelected() )
            {
                alpha = 80;
                paintBackground( g, width, height, alpha );
            }
            else
            {
                paintBackground( g, width, height, alpha );
            }
            if (width <=5)
            {
            	return;
            }
            
            //boolean isException = getAppointment().getRepeating().isException(getStart().getTime());
            Color fg = adjustColor( FOREGROUND_COLOR, alpha ); //(isException() ? Color.white : Color.black);
            g.setColor( fg );

            if ( getAppointment().getRepeating() != null && getBuildContext().isRepeatingVisible() )
            {
                if ( !getContext().isAnonymous() && getContext().isBlockSelected() && !isException() )
                {
                    getRepeatingIcon().paintIcon( this, g, width - 17, 0 );
                }
                /*
                 if ( getBuildContext().isTimeVisible() )
                 g.clipRect(0,0, width -17, height);
                 */
            }
            // y will store the y-position of the carret
            int y = -2;
            // Draw the Reservationname
            boolean small = (height < 30);
           
            String timeString = getTimeString(small);
            StringBuffer buf = new StringBuffer();
            if ( timeString != null )
            {
                if ( !small)
                {
                    g.setFont( FONT_SMALL_TITLE );
                    List<Allocatable> resources = getContext().getAllocatables();
                    StringBuffer buffer  = new StringBuffer() ;
                    for (Allocatable resource: resources)
                    {
                    	if ( getContext().isVisible( resource) && !resource.isPerson())
                    	{
                    		if ( buffer.length() > 0)
                    		{
                    			buffer.append(", ");
                    		}
                    		buffer.append( getName(resource));
                    	}
                    }
                    if (  !getBuildContext().isResourceVisible() && buffer.length() > 0)
                    {
                    	timeString = timeString + " " + buffer;
                    }
                    y = drawString( g, timeString, y, 2, false ) - 1;
                }
                else
                {
                    buf.append( timeString );
                    buf.append( " " );
                }
            }
            if ( !small)
            {
                g.setFont( FONT_TITLE );
            }
            else
            {
                g.setFont( FONT_SMALL_TITLE );
            }

            if ( getContext().isAnonymous() )
            {
                y += 4;
                g.setFont( FONT_INVISIBLE );
                String label = getI18n().getString( "not_visible" );
                buf.append(label);
                y = drawString( g, buf.toString(), y, 5, true ) + 2;
                return;
            }

            final Reservation reservation = getReservation();
            String label = getReservationName();
            buf.append(label);
            y = drawString( g, buf.toString(), y, 2, true ) + 2;

            // If the y reaches the lowerBound "..." will be displayed
            double lowerBound = height - 11;

            if ( getBuildContext().isPersonVisible() )
            {
                g.setFont( FONT_PERSON );
                g.setColor( fg );
                List<Allocatable> persons = getContext().getAllocatables();
                for ( Allocatable person:persons)
                {
                  String text = getName( person, reservation);
                  if ( !getContext().isVisible( person) || !person.isPerson())
                	  continue;
                    if ( y > lowerBound )
                    {
                        text = "...";
                        y -= 7;
                    }
                    y = drawString( g, text, y, 7, true );
                }
            }
            else
            {
            	   g.setFont( FONT_PERSON );
                   g.setColor( fg );
                   buf = new StringBuffer();
                   List<Allocatable> persons = getContext().getAllocatables();
                   for ( Allocatable person:persons)
                   {
                	   if ( !getContext().isVisible( person) || !person.isPerson())
                     	  continue;
                     
                	   if ( buf.length() > 0)
                       {
                    	   buf.append(", ");
                       }
                       buf.append( getName( person, reservation ));
                   }
                   String text = buf.toString();
                   y = drawString( g, text, y, 7, true );
               
            }

            if ( getBuildContext().isResourceVisible() )
            {
            	List<Allocatable> resources = getContext().getAllocatables();
                g.setFont( FONT_RESOURCE );
                g.setColor( fg );
                for ( Allocatable resource:resources)
                {
                	 if ( !getContext().isVisible( resource) || resource.isPerson())
                    	  continue;
                	String text = getName( resource, reservation );
                    if ( y > lowerBound )
                    {
                        text = "...";
                        y -= 7;
                    }
                    y = drawString( g, text, y, 7, true );
                }
            }
        }

        private void setExceptionPaint( Graphics g )
        {
            Paint p = new TexturePaint( getExceptionImage(), new Rectangle( 14, 14 ) );
            ( (Graphics2D) g ).setPaint( p );
        }

        private void paintBackground( Graphics g, int width, int height, int alpha )
        {
            String[] colors = getColorsAsHex();
            double colWidth = (double) ( width - 2 ) / colors.length;
            int x = 0;
            for ( int i = 0; i < colors.length; i++ )
            {
                g.setColor( adjustColor( colors[i], alpha ) );
                g.fillRect( (int) Math.ceil( x ) + 1, 1, (int) Math.ceil( colWidth ), height - 2 );
                if ( isException() )
                {
                    setExceptionPaint( g );
                    g.fillRect( (int) Math.ceil( x ) + 1, 1, (int) Math.ceil( colWidth ), height - 2 );
                }
                x += colWidth;
            }

            //g.setColor( adjustColor( "#000000", alpha ) );
            g.setColor( linecolor );
            g.drawRoundRect( 0, 0, width - 1, height - 1, 5, 5 );
        }

        private int findBreaking( final char[] c, final int offset, final int len, final int maxWidth, final FontMetrics fm, final boolean allCharacters )
        {
            int index = -1;
            for ( int i = offset; i < offset + len; i++ )
            {
                if ( allCharacters || c[i] == ' ' )
                {
                	int charsWidth = fm.charsWidth( c, offset, i - offset );
					if ( charsWidth < maxWidth )
                	{
                		index = i;
                	}
                	else
                	{
                		return index;
                	}
                }
            }
            return index;
        }
        
        // @return the new y-coordiante below the text
        private int drawString( Graphics g, String text, int y, int indent, boolean breakLines )
        {
            FontMetrics fm = g.getFontMetrics();
            //g.setFont(new Font("SimSun",Font.PLAIN, 12));
            char[] c = text.toCharArray();
            int cWidth = getSize().width - indent;
            int cHeight = getSize().height;
            int height = fm.getHeight();
            int len = c.length;
            int offset = 0;
            int x = indent;
            int maxWidth = ( y >= 14 || getAppointment().getRepeating() == null || !getBuildContext()
                                                                                                     .isRepeatingVisible() )
                    ? cWidth
                    : cWidth - 12;
            if ( !breakLines )
            {
                maxWidth = maxWidth - 5;
            }
            else
            {
            	if (breakLines)
            	{
	                while ( offset < c.length && fm.charsWidth( c, offset, len ) > maxWidth )
	                {
	                    int breakingSpace = findBreaking( c, offset, len, maxWidth, fm, false );
	                    //int x = bCenter ? (getSize().width - width)/2 : indent ;
	                    if ( breakingSpace >= offset )
	                    {
	                        y+= height;
	                    	int length = breakingSpace-offset;
							g.drawChars(c,offset,length,x,y);
	                        //              System.out.println("Drawing " + new String(c,offset,breakingSpace-offset));
	                        len -= length + 1;
	                        offset = breakingSpace + 1;
	                    }	                    
	                    else
	                    {
		                    int breakingChar = findBreaking( c, offset, len, maxWidth, fm, true );
		                    if ( breakingChar >= offset)
		                    {
		                        y+= height;
		                    	int length = breakingChar-offset;
		                    	if ( c.length >= offset + length)
		                    	{
		                    		g.drawChars(c,offset,length,x,y);
		                    	}
		                    	else
		                    	{
		                    		getContext().getBuildContext().getLogger().error("wrong offset[" + offset + "] or length["+ length + "] for string '"+ text + "'");
		                    	}
		                        //              System.out.println("Drawing " + new String(c,offset,breakingSpace-offset));
		                        len -= length ;
		                        offset = breakingChar;
		                    }
		                    else
		                    {
		                    	break;
		                    }
	                    }
	                    if (y >= cHeight)
	                    {
	                    	break;
	                    }
	                    //      System.out.println("New len " + len + " new offset " + offset);
	                    maxWidth = cWidth;
	                }
            	}
            }
            if (y <= cHeight - height/2 )
            {
            	y = y + height;
            	if  (c.length >= offset + len)
            	{
            		g.drawChars( c, offset, len, x, y );
            	}
            	else
            	{
            		getContext().getBuildContext().getLogger().error("wrong offset[" + offset + "] or length["+ len + "] for string '"+ text + "'");
            	}
            }
            //      System.out.println("Drawing rest " + new String(c,offset,len));
            return y;
        }

        public void mouseClicked( MouseEvent arg0 )
        {

        }

        public void mousePressed( MouseEvent arg0 )
        {}

        public void mouseReleased( MouseEvent evt )
        {
            Point mp = evt.getPoint();
            boolean inside = mp.x >= 0 && mp.y >= 0 && mp.x <= getWidth() && mp.y <= getHeight();
            changeLineBorder( inside && getContext().isBlockSelected() );
        }

        public void mouseEntered( MouseEvent evt )
        {
            if ( ( ( evt.getModifiers() & MouseEvent.BUTTON1_MASK ) > 0 ) || !getContext().isBlockSelected() )
            {
                return;
            }
            changeLineBorder( true );
        }

        public void mouseExited( MouseEvent evt )
        {
            if ( ( evt.getModifiers() & MouseEvent.BUTTON1_MASK ) > 0 )
            {
                return;
            }
            changeLineBorder( false );
        }

        public void mouseDragged( MouseEvent arg0 )
        {}

        public void mouseMoved( MouseEvent arg0 )
        {
        }

        private void changeLineBorder( boolean active )
        {
            List<Block> blocks = getBuildContext().getBlocks();
            for ( Iterator<Block> it = blocks.iterator(); it.hasNext(); )
            {
                SwingRaplaBlock block = (SwingRaplaBlock) it.next();

                Appointment appointment = block.getAppointment();
                Reservation reservation = appointment.getReservation();
                // A reservation can be null because the appointment was recently removed from the reservation and the swing view had no time to rebuild
                if ( reservation == null)
                {
                	return;
                }
				if ( appointment.equals( getAppointment() ) )
                {
                    block.linecolor = active ? LINECOLOR_ACTIVE : LINECOLOR_INACTIVE;
                    block.m_view.repaint();
                }
                else if ( reservation.equals( getReservation() ) )
                {
                    block.linecolor = active ? LINECOLOR_SAME_RESERVATION : LINECOLOR_INACTIVE;
                    block.m_view.repaint();
                }
            }
        }

    }
    
    public String toString()
    {
    	return getName() + " " + getStart() + " - " + getEnd();
    }

}
