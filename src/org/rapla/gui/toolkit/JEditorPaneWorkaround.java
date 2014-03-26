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
package org.rapla.gui.toolkit;
import java.awt.Dimension;
import java.awt.FontMetrics;

import javax.swing.JEditorPane;

/** #BUGFIX
 * This is a workaround for a bug in the Sun JDK
 * that don't calculate the correct size of an JEditorPane.
 * The first version of this workaround caused a NullPointerException
 * on JDK 1.2.2 sometimes, so this is a workaround for a workaround:
 * A zero-sized component is added to the StartFrame- Window
 * This component will be used to calculate the size of
 * the JEditorPane Components.
 */
final class JEditorPaneWorkaround {
    static public void packText(JEditorPane jText,String text,int width) {
        int height;
	if (width <=0 )
	    return;
        try {
            jText.setSize(new Dimension(width,100));
            jText.setText(text);
            height = jText.getPreferredScrollableViewportSize().height;
        } catch ( NullPointerException e) {
            jText.setSize(new Dimension(width,100));
            jText.setText(text);
	    FontMetrics fm = jText.getFontMetrics(jText.getFont());
	    height = fm.stringWidth(text)/width * fm.getHeight() + 50;
        } // end of try-catch
        jText.setSize(new Dimension(width,height));
        jText.setPreferredSize(new Dimension(width,height));
    }
}











