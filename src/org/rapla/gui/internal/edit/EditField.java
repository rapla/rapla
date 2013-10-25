/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Christopher Kohlhaas                                  |
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
package org.rapla.gui.internal.edit;

import javax.swing.event.ChangeListener;

import org.rapla.gui.toolkit.RaplaWidget;

/** Base class for most rapla edit fields. Provides some mapping
    functionality such as reflection invocation of getters/setters.
    A fieldName "username" will result in a getUsername() and setUsername()
    method.
*/
public interface EditField extends RaplaWidget
{
    public boolean isBlock();
    public boolean isVariableSized();
    public String getFieldName();
    // public void mapTo(List<T> o) throws RaplaException;
    // public void mapFrom(List<T> o) throws RaplaException;
    /** registers new ChangeListener for this component.
     *  An ChangeEvent will be fired to every registered ChangeListener
     *  when the component info changes.
     * @see javax.swing.event.ChangeListener
     * @see javax.swing.event.ChangeEvent
    */
    public void addChangeListener(ChangeListener listener);
    /** removes a listener from this component.*/
    public void removeChangeListener(ChangeListener listener);
}

