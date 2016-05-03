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

import javax.swing.JComponent;
import javax.swing.event.ChangeListener;

import org.rapla.client.RaplaWidget;

/** Base class for most rapla edit fields. Provides some mapping
    functionality such as reflection invocation of getters/setters.
    A fieldName "username" will result in a getUsername() and setUsername()
    method.
*/
public interface EditField extends RaplaWidget<JComponent>
{
    String getFieldName();
    /** registers new ChangeListener for this component.
     *  An ChangeEvent will be fired to every registered ChangeListener
     *  when the component info changes.
     * @see javax.swing.event.ChangeListener
     * @see javax.swing.event.ChangeEvent
    */
    void addChangeListener(ChangeListener listener);
    /** removes a listener from this component.*/
    void removeChangeListener(ChangeListener listener);
}

