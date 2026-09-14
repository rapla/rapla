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
package org.rapla.client.swing;

import org.rapla.client.RaplaWidget;
import org.rapla.entities.Named;
import org.rapla.entities.configuration.Preferences;
import org.rapla.framework.RaplaException;

public interface OptionPanel extends RaplaWidget, Named {
    void setPreferences(Preferences preferences);
    
    /** commits the changes in the option Dialog.*/
    void commit() throws RaplaException;

    /** called when the option Panel is selected for displaying.*/
    void show() throws RaplaException;


}

