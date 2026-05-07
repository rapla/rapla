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
package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.client.internal.RaplaColors;
import org.rapla.client.swing.toolkit.AWTColorUtil;

import javax.swing.JLabel;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;

/** A label with a background-color corresponding to the index
    of the appointment.
    @see RaplaColors#getAppointmentColor
 */
public class AppointmentIdentifier extends JLabel {
    private static final long serialVersionUID = 1L;

    String text;
    int index = 0;
    public void setIndex(int index) {
	this.index = index;
    }
    public void setText(String text) {
	this.text = text;
	super.setText(text + " ");
    }

    public void paintComponent(Graphics g) {		    
	FontMetrics fm = g.getFontMetrics();
	Insets insets = getInsets();
	String s = text;
	int width = fm.stringWidth(s);
	int x = 1;
	g.setColor(AWTColorUtil.getAppointmentColor(index));
	g.fillRoundRect(x
			,insets.top
			,width +1
			,getHeight()-insets.top -insets.bottom-1,4,4);
	g.setColor(getForeground());
	g.drawRoundRect(x-1
			,insets.top
			,width +2
			,getHeight()-insets.top -insets.bottom-1,4,4);
	g.drawString(s
		     ,x
		     ,getHeight() /2  + fm.getDescent() + 1);
    }
}
