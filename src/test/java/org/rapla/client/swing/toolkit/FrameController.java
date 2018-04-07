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

import java.awt.Cursor;

/**  All classes implementing this Interface must call
     FrameControllerList.addFrameController(this) on initialization
     FrameControllerList.removeFrameController(this) on close
     This Class is used for automated close of all Frames on Logout.
*/
public interface FrameController {
    void close(); // must call FrameControllerList.remove(this);
	void setCursor(Cursor cursor);
 }





