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
package org.rapla.components.util.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** concatenates two Iterators */
public class IteratorChain<T> implements Iterator<T> {
    protected Iterator<T> firstIt;
    protected Iterator<T> secondIt;
    protected Iterator<T> thirdIt;

    public IteratorChain(Iterator<T> firstIt, Iterator<T> secondIt) {
        this.firstIt = firstIt;
        this.secondIt = secondIt;
    }

    public IteratorChain(Iterator<T> firstIt, Iterator<T> secondIt, Iterator<T> thirdIt) {
        this.firstIt = firstIt;
        this.secondIt = secondIt;
        this.thirdIt  = thirdIt;
    }

    public boolean hasNext() {
        return  (firstIt!=null && firstIt.hasNext()) || (secondIt != null && secondIt.hasNext()) || (thirdIt != null && thirdIt.hasNext());
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public T next() {
        if (firstIt!=null  && !firstIt.hasNext())
        {
        	firstIt = null;
        }
        if (secondIt!=null  && !secondIt.hasNext())
        {
        	secondIt = null;
        }
//        else if (thirdIt!=null  && !thirdIt.hasNext())
//        {
//        	thirdIt = null;
//        }
            
        if ( firstIt != null )
            return firstIt.next();
        else if ( secondIt != null)
            return secondIt.next();
        else if (thirdIt != null)
        	return thirdIt.next();
        else
        	throw new NoSuchElementException();
    }

	
}

