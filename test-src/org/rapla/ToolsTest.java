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

import junit.framework.TestCase;

import org.rapla.components.util.Tools;


public class ToolsTest extends TestCase {
    public ToolsTest(String name) {
        super(name);
    }
    public void testEqualsOrBothNull() {
        Integer a = new Integer( 1 );
        Integer b = new Integer( 1 );
        Integer c = new Integer( 2 );
        assertTrue ( a != b );
        assertEquals ( a, b );
        assertTrue( Tools.equalsOrBothNull( null, null ) );
        assertTrue( !Tools.equalsOrBothNull( a, null ) );
        assertTrue( !Tools.equalsOrBothNull( null, b ) );
        assertTrue( Tools.equalsOrBothNull( a, b ) );
        assertTrue( !Tools.equalsOrBothNull(  b, c ) );
    }
}
