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

/** concatenates two Iterators */
public class IteratorChain<T> implements Iterator<T> {
    protected Iterator<T> firstIt;
    protected Iterator<T> secondIt;
    boolean isIteratingFirst = true;

    public IteratorChain(Iterator<T> firstIt, Iterator<T> secondIt) {
        this.firstIt = firstIt;
        this.secondIt = secondIt;
    }


    public boolean hasNext() {
        return  (isIteratingFirst && firstIt.hasNext())
                || secondIt.hasNext();
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public T next() {
        if (isIteratingFirst && !firstIt.hasNext())
            isIteratingFirst = false;

        if ( isIteratingFirst )
            return firstIt.next();
        else
            return secondIt.next();
    }
}

