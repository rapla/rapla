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

package org.rapla.components.util;

import java.util.Comparator;

/**
 *
 * Reverts the Original Comparator
 *  -1 -&gt;  1
 *   1 -&gt; -1
 *   0 -&gt;  0
 */
public class InverseComparator<T> implements Comparator<T> {
    Comparator<T> original;
    public InverseComparator( Comparator<T> original) {
        this.original = original;
    }
    public int compare( T arg0, T arg1 )
    {
        return -1 * original.compare( arg0, arg1);
    }
}


