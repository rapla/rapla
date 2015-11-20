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
package org.rapla;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.rapla.components.util.Tools;

@RunWith(JUnit4.class)
public class ToolsTest
{

    @Test
    public void testEqualsOrBothNull()
    {
        Integer a = new Integer(1);
        Integer b = new Integer(1);
        Integer c = new Integer(2);
        Assert.assertTrue(a != b);
        Assert.assertEquals(a, b);
        Assert.assertTrue(Tools.equalsOrBothNull(null, null));
        Assert.assertTrue(!Tools.equalsOrBothNull(a, null));
        Assert.assertTrue(!Tools.equalsOrBothNull(null, b));
        Assert.assertTrue(Tools.equalsOrBothNull(a, b));
        Assert.assertTrue(!Tools.equalsOrBothNull(b, c));
    }
}
