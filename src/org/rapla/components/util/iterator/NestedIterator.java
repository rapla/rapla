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

/**Successivly iterates over the elements specified in the nested Iterators.
Example of an recursive traversal of an Entity Tree:
<pre>
class RecursiveEntityIterator extends NestedIterator {
    public RecursiveEntityIterator(Iterator it) {
        super(it);
    }
    public Iterator getNestedIterator(Object obj) {
        return new RecursiveEntityIterator(((Entity)obj).getSubEntities());
    }
}
</pre>
*/

public abstract class NestedIterator<T,S> implements Iterator<T>, Iterable<T> {
    protected Iterator<S> outerIt;
    protected Iterator<T> innerIt;
    T nextElement;
    boolean isInitialized;
    public NestedIterator(Iterable<S> outerIt) {
        this.outerIt = outerIt.iterator();
    }

    private T nextElement() {
        while (outerIt.hasNext() || (innerIt != null && innerIt.hasNext())) {
            if (innerIt != null && innerIt.hasNext())
                return innerIt.next();
            innerIt = getNestedIterator(outerIt.next()).iterator();
        }
        return null;
    }

    public abstract Iterable<T> getNestedIterator(S obj);

    public boolean hasNext() {
        if (!isInitialized)
        {
            nextElement = nextElement();
            isInitialized = true;
        }
        return nextElement != null;
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }

    public T next() {
        if (!hasNext())
            throw new NoSuchElementException();
        T result = nextElement;
        nextElement = nextElement();
        return result;
    }
    
    public Iterator<T> iterator() {
    	return this;
    }
}

