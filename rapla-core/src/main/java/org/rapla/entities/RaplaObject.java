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
package org.rapla.entities;


/**This interface is a marker to distinct the different rapla classes
 * like Reservation, Allocatable and Category.
 * It is something like the java instanceof keyword. But it must be unique for each
 * class.  This type-information is for examle used for mapping the correct storage-,
 * editing- mechanism to the class.
 */
public interface RaplaObject<T> extends Cloneable {
    String[] EMPTY_STRING_ARRAY = new String[0];

    Class<T> getTypeClass();

	T clone();
}







