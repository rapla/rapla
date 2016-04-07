/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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
package org.rapla.components.calendarview.swing;

import java.awt.Component;
import java.awt.Point;
import java.util.Date;

import org.rapla.components.calendarview.Block;

/** Listeners for user-changes in the weekview.*/
public interface ViewListener {
    /** Invoked when the user invokes the slot-contex (right-clicks on slot).
        The selected area and suggested
        coordinates at which the popup menu can be shown are passed.*/
    void selectionPopup(Component slotComponent,Point p,Date start,Date end, int slotNr);
    /** Invoked when the selection has changed.*/
    void selectionChanged(Date start,Date end);
    /** Invoked when the user invokes a block-context (right-clicks on a block).
        The suggested coordinates at which the popup menu can be shown are passed.*/
    void blockPopup(Block block,Point p);
    /** Invoked when the user invokes a block-edit (double-clicks on a block).
        The suggested coordinates at which the popup menu can be shown are passed.*/
    void blockEdit(Block block,Point p);
    /** Invoked when the user has dragged/moved a block */
    void moved(Block block,Point p,Date newStart, int slotNr);
    /** Invoked when the user has resized a block */
    void resized(Block block,Point p,Date newStart, Date newEnd, int slotNr);
}







