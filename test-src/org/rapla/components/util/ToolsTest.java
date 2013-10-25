package org.rapla.components.util;

import junit.framework.TestCase;

public class ToolsTest extends TestCase
{
    public ToolsTest(String name) {
        super( name);
    }

//    public void testReplace() {
//        String newString = Tools.replaceAll("Helllo Worlld llll","ll","l" );
//        assertEquals( "Hello World ll", newString);
//    }

    public void testSplit() {
        String[] result = Tools.split("a;b2;c",';');
        assertEquals( "a", result[0]);
        assertEquals( "b2", result[1]);
        assertEquals( "c", result[2]);
    }
}
