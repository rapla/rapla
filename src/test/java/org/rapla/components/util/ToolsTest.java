package org.rapla.components.util;

import junit.framework.TestCase;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.Assert;

@RunWith(JUnit4.class)
public class ToolsTest
{

    @Test
    public void testSplit() {
        String[] result = Tools.split("a;b2;c",';');
        Assert.assertEquals("a", result[0]);
        Assert.assertEquals("b2", result[1]);
        Assert.assertEquals("c", result[2]);
    }
}
