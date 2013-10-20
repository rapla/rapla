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

/** Filters the objects of an Iterator by overiding the isInIterator method*/
public abstract class FilterIterator<T> implements Iterator<T> {
    Iterator<? extends Object> it;
    Object obj;

    public FilterIterator(Iterator<? extends Object> it) {
        this.it = it;
        obj = getNextObject();
    }

    public boolean hasNext() {
        return  obj != null;
    }

    protected abstract boolean isInIterator(Object obj);

    public void remove() {
        throw new UnsupportedOperationException();
    }

    private Object getNextObject() {
    	Object o;
        do {
            if ( !it.hasNext() ) {
                return null;
            }
            o =  it.next();
        } while (!isInIterator( o));
        return o;
    }

    @SuppressWarnings("unchecked")
	public T next() {
        if ( obj ==  null)
            throw new NoSuchElementException();

        Object result = obj;
        obj = getNextObject();
        return (T)result;
    }
}

