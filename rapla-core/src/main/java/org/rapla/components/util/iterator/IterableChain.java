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

/** concatenates two Iterators */
public class IterableChain<T> implements Iterable<T> {
    protected Iterable<T> firstIt;
    protected Iterable<T> secondIt;
    protected Iterable<T> thirdIt;

    public IterableChain(Iterable<T> firstIt, Iterable<T> secondIt, Iterable<T> thirdIt)
    {
        this.firstIt = firstIt;
        this.secondIt = secondIt;
        this.thirdIt  = thirdIt;
    }
    
    public IterableChain(Iterable<T> firstIt, Iterable<T> secondIt)
    {
        this.firstIt = firstIt;
        this.secondIt = secondIt;
    }


    @Override
    public Iterator<T> iterator() 
    {
        return new IteratorChain<>(firstIt != null ? firstIt.iterator() : null, secondIt != null ? secondIt.iterator() : null, thirdIt != null ? thirdIt.iterator() : null);
    }
	
}

