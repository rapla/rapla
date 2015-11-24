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
package org.rapla.components.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.iterator.FilterIterable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@RunWith(JUnit4.class)
public class FilterIteratorTest  {


    @Test
    public void testIterator() {
        List<Integer> list = new ArrayList<Integer>();
        for (int i=0;i<6;i++) {
            list.add( new Integer(i));
        }
        Iterator<Integer> it = (new FilterIterable<Integer>(list) {
            protected boolean isInIterator( Object obj )
            {
                return ((Integer) obj).intValue() % 2 ==0; 
            }
        }).iterator();
        for (int i=0;i<6;i++) {
            if ( i % 2 == 0)
                Assert.assertEquals(new Integer(i), it.next());
        }
        Assert.assertTrue(it.hasNext() == false);
    }
}





