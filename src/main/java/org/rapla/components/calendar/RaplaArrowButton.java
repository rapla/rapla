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
package org.rapla.components.calendar;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class RaplaArrowButton extends JButton  {
    private static final long serialVersionUID = 1L;

    ButtonStateChecker m_checker = new ButtonStateChecker();
    
    int m_delay = 0;
    boolean m_buttonDown = false;
    
    ArrowPolygon poly;
    char c;

    public RaplaArrowButton(char c) {
        this(c,18);
     
    }    

    public RaplaArrowButton(char c,int size) {
        super();
        this.c = c;
        setMargin(new Insets(0,0,0,0));
        setSize(size,size);
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent me) {
                if (!isEnabled())
                    return;
                m_buttonDown = true;
               // repaint();
                m_checker.start();
            }
            /** Implementation-specific. Should be private.*/
            public void mouseReleased(MouseEvent me) {
                m_buttonDown = false;
             //   repaint();
            }

            /** Implementation-specific. Should be private.*/
            public void mouseClicked(MouseEvent me) {
                m_buttonDown = false;
                //repaint();
            } 
        });
    }

    /** Here you can set if the button should fire repeated clicks. If
    set to 0 the button will fire only once when pressed.
 */
	public void setClickRepeatDelay(int millis) {
	    m_delay = millis;
	}
	
	public int getClickRepeatDelay() {
	    return m_delay;
	}

	public boolean isOpen()
	{
	    return c == '^';
	}
	
	public void setChar( char c)
	{
	    this.c = c;
	    final Dimension size = getSize();
        final int width2 = (int)size.getWidth();
        final int height2 = (int)size.getHeight();
        setSize( width2,height2);
	}
    /** Set the size of the drop-down button. 
        The minimum of width and height will be used as new size of the arrow.
     */
    public void setSize(int width,int height) {
        int size = Math.min(width,height);
        int imageSize = size - 8;
        if (imageSize > 0) {
            poly = new ArrowPolygon(c,imageSize);
            BufferedImage image = new BufferedImage(imageSize,imageSize,BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.createGraphics();
            g.setColor(new Color(0, 0, 0, 0));
            g.fillRect( 0, 0, imageSize, imageSize );

            g.setColor( Color.darkGray );
            g.fillPolygon( poly );
            g.setColor( Color.black );
            g.drawPolygon( poly );
            setIcon(new ImageIcon(image));
        } else {
            setIcon(null);
        }
        super.setSize(width ,height);
        Dimension dim = new Dimension(width ,height);
        setPreferredSize(dim);
        setMaximumSize(dim);
        setMinimumSize(dim);
    }
    
    class ButtonStateChecker implements Runnable{
        long startMillis;
        long startDelay;
        public void start() {
            startDelay = m_delay * 10;
            startMillis = System.currentTimeMillis();
            if (m_delay > 0)
                SwingUtilities.invokeLater(this);
        }

        private void fireAndReset() {
            fireActionPerformed(new ActionEvent(this
                                                ,ActionEvent.ACTION_PERFORMED
                                                ,""));
            startMillis = System.currentTimeMillis();
        }
        public void run() {
            if (!m_buttonDown)
                return;
            if ((Math.abs(System.currentTimeMillis() - startMillis) > startDelay)) {
                if (startDelay > m_delay)
                    startDelay = startDelay/2;
                fireAndReset();
            }
            try {
                Thread.sleep(10);
            } catch (Exception ex) {
                return;
            }
            SwingUtilities.invokeLater(this);
        }
    }
}

