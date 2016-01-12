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
package org.rapla.components.xmlbundle.tests;

import org.junit.Assert;
import org.junit.Test;
import org.rapla.components.xmlbundle.I18nBundle;

import java.util.MissingResourceException;

public abstract class AbstractI18nTest  {
    abstract public I18nBundle getI18n();

	@Test
    public void testGetString() { 
    	String string = getI18n().getString("cancel");
    	if (getI18n().getLocale().getLanguage().equals("de"))
    	    Assert.assertEquals(string, "Abbrechen");
    	if (getI18n().getLocale().getLanguage().equals("en"))
			Assert.assertEquals(string, "cancel");
    	boolean failed = false;
    	try {
    	    string = getI18n().getString("this_string_request_should_fail.");
    	} catch (MissingResourceException ex) { 
    	    failed = true;
    	}
		Assert.assertTrue("Request for 'this_string_request_should_fail should throw MissingResourceException", failed);
    }

	@Test
    public void testLocale() {
		Assert.assertNotNull(getI18n().getLocale());
		Assert.assertNotNull(getI18n().getLang());
    }

	@Test
    public void testXHTML() {
		Assert.assertTrue("<br/> should be replaced with <br>", getI18n().getString("error.invalid_key").indexOf("<br>") >= 0);
    }
}





