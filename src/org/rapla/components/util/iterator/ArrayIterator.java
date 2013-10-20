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
package org.rapla.components.util.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;
/** Iterate over an Array*/
public class ArrayIterator<T> implements Iterator<T> {
    T[] array;
    int pos = 0;

    public ArrayIterator(T[] array) {
        this.array = array;
    }

    public boolean hasNext() {
        return  pos < array.length ;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public T next() {
        if ( pos >=  array.length)
            throw new NoSuchElementException();

        return array[ pos ++];
    }
}

