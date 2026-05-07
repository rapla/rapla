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

/** Some of the assert functionality of 1.4 for 1.3 versions of Rapla*/
public class Assert {
    static String NOT_NULL_ASSERTION = "notNull-Assertion";
    static String IS_TRUE_ASSERTION = "isTrue-Assertion";
    static String ASSERTION_FAIL = "Assertion fail";
    static boolean _bActivate = true;

    public static void notNull(Object obj,String text) {
        if ( obj == null && isActivated()) {
	    doAssert(getText(NOT_NULL_ASSERTION,text));
        }
    }

    public static void notNull(Object obj) {
        if ( obj == null && isActivated()) {
	    doAssert(getText(NOT_NULL_ASSERTION,""));
        }
    }

    public static void isTrue(boolean condition,String text) {
        if ( !condition && isActivated()) {
            doAssert(getText(IS_TRUE_ASSERTION,text));
        } // end of if ()
    }

    public static void isTrue(boolean condition) {
        if ( !condition && isActivated()) {
            doAssert(getText(IS_TRUE_ASSERTION,""));
        } // end of if ()
    }

    public static void fail() throws AssertionError {
        doAssert(getText(ASSERTION_FAIL,""));
    }

    public static void fail(String text) throws AssertionError {
        doAssert(getText(ASSERTION_FAIL,text));
    }

    private static void doAssert(String text) throws AssertionError {
	System.err.println(text);
	throw new AssertionError(text);
    }

    static boolean isActivated() {
        return _bActivate;
    }

    static void setActivated(boolean bActivate) {
        _bActivate = bActivate;
    }

    static String getText(String type, String text) {
        return ( type + " failed '" + text + "'");
    }
}






