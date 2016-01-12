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
package org.rapla.client.swing.toolkit;

import javax.swing.Action;
import javax.swing.JButton;
import java.awt.Insets;

public class RaplaButton extends JButton {
    private static final long serialVersionUID = 1L;
    
    public static int SMALL= -1;
    public static int LARGE = 1;
    public static int DEFAULT = 0;

    private static Insets smallInsets = new Insets(0,0,0,0);
    private static Insets largeInsets = new Insets(5,10,5,10);

    public RaplaButton(String text,int style) {
        this(style);
        setText(text);
    }

    public RaplaButton(int style) {
        if (style == SMALL) {
            setMargin(smallInsets);
        } else if (style == LARGE) {
            setMargin(largeInsets);
        } else {
            setMargin(null);
        }
    }

    public void setAction(Action action) {
        String oldText = null;
        if (action.getValue(Action.NAME) == null)
            oldText = getText();
        super.setAction(action);
        if (oldText != null)
            setText(oldText);
    }
    public RaplaButton() {
    }


}



