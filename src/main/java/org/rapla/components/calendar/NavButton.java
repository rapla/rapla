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

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ButtonUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Polygon;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

public class NavButton extends AbstractButton implements MouseListener {
    private static final long serialVersionUID = 1L;

    Polygon m_poly;
    char m_type;
    boolean m_buttonDown = false;
    Color m_disabledColor;
    boolean m_enabled=true;
    boolean m_border=true;
    int m_delay = 0;
    int leftPosition;

    ButtonStateChecker m_checker = new ButtonStateChecker();
    /** You can set the alignment of the arrow with one of these four characters:
        '&lt;', '^', '&gt;', 'v'
     */
    public NavButton(char type) {
        this(type,18);
    }

    public NavButton(char type,int size) {
        this(type,size,true);
    }

    public NavButton(char type,int size,boolean border) {
        super();
        m_border = border;
        m_type = type;
        setSize(size,size);
        addMouseListener(this);
        m_disabledColor = UIManager.getColor("Button.disabledText");
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

    public void setUI(ButtonUI ui) {
        super.setUI(ui);
        m_disabledColor = UIManager.getColor("Button.disabledText");
    }
    
    void setLeftPosition(int position) {
        leftPosition = position;
    }

    /** Implementation-specific. Should be private.*/
    public void mouseEntered(MouseEvent me) {
    }
    /** Implementation-specific. Should be private.*/
    public void mouseExited(MouseEvent me) {
    }

    /** Implementation-specific. Should be private.*/
    public void mousePressed(MouseEvent me) {
        if (!isEnabled())
            return;
        m_buttonDown = true;
        repaint();
        m_checker.start();
    }
    /** Implementation-specific. Should be private.*/
    public void mouseReleased(MouseEvent me) {
        m_buttonDown = false;
        repaint();
    }

    /** Implementation-specific. Should be private.*/
    public void mouseClicked(MouseEvent me) {
        m_buttonDown = false;
        repaint();
    }

    /** Set the size of the nav-button. A nav button is square.
        The maximum of width and height will be used as new size.
     */
    public void setSize(int width,int height) {
        int size = Math.max(width,height);
        m_poly = new ArrowPolygon(m_type,size,m_border);
        super.setSize(size,size);
        Dimension dim = new Dimension(size,size);
        setPreferredSize(dim);
        setMaximumSize(dim);
        setMinimumSize(dim);
    }

    public void setEnabled(boolean enabled) {
        m_enabled = enabled;
        repaint();
    }
    public boolean isEnabled() {
        return m_enabled;
    }
    public void paint(Graphics g) {
        g.translate( leftPosition, 0);
        if (m_buttonDown) {
            //g.setColor( UIManager.getColor("Button.pressed"));
            g.setColor( UIManager.getColor("Button.focus"));
            g.fillPolygon(m_poly);
            g.drawPolygon(m_poly);
        } else {
            if (isEnabled()) {
                g.setColor( UIManager.getColor("Button.font") );
                g.fillPolygon(m_poly);
            } else {
                g.setColor(m_disabledColor);        
            }
            g.drawPolygon(m_poly);
        }
    }
        

    class ButtonStateChecker implements Runnable{
        long startMillis;
        long startDelay;
        public void start() {
            startDelay = m_delay * 8;
            fireAndReset();
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


