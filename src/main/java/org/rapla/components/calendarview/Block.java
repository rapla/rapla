/*--------------------------------------------------------------------------*
 | Copyright (C) 2006 Gereon Fassbender                                     |
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

package org.rapla.components.calendarview;

import jsinterop.annotations.JsType;

import java.util.Date;
/**
implementierung von block koennen in ein slot eingefuegt werden.
Sie dienen als modell fuer beliebige grafische komponenten.
mit getStart() und getEnd() wird anfangs- und endzeit des blocks definiert
(nur uhrzeit ist fuer positionierung in slots relevant).
*/
@JsType
public interface Block
{
    Date getStart();
    Date getEnd();
    String getName();
}







