/*--------------------------------------------------------------------------*
| Copyright (C) 2013 Christopher Kohlhaas                                  |
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
package org.rapla.framework.logger.internal;

import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
@DefaultImplementation(of = org.rapla.framework.logger.Logger.class,context = InjectionContext.gwt)
public class RaplaJDKLoggingAdapterForGwt implements org.rapla.framework.logger.Logger {
    Logger logger;
    String id;

    @Inject
    public RaplaJDKLoggingAdapterForGwt()
    {
        this(getLogger("rapla"), "rapla");
    }

    private RaplaJDKLoggingAdapterForGwt( Logger logger, String id) {
        this.logger = logger;
        this.id = id;
    }

	static private Logger getLogger(String categoryName)
	{
		Logger logger = Logger.getLogger(categoryName);
		return logger;
	}

    protected void log_(Logger logger, Level level, String message, Throwable cause) {
        logger.log(level,message, cause);
    }

    public boolean isDebugEnabled() {
        return logger.isLoggable(Level.CONFIG);
    }

    public void debug(String message) {
        log(Level.CONFIG, message);
    }

    public void info(String message) {
        log(Level.INFO, message);
    }

    public void warn(String message) {
        log(Level.WARNING,message);
    }

    public void warn(String message, Throwable cause) {
        log(Level.WARNING,message, cause);
    }

    public void error(String message) {
        log(Level.SEVERE, message);
    }

    public void error(String message, Throwable cause) {
        log(Level.SEVERE, message, cause);
    }

    private void log(Level level,String message) {
        log(level, message, null);
    }

    private void log(Level level,String message, Throwable cause) {
        log_(logger, level, message, cause);
    }


    public org.rapla.framework.logger.Logger getChildLogger(String childLoggerName)
    {
        String childId = id+ "." + childLoggerName;
        Logger childLogger = getLogger( childId);
        return new RaplaJDKLoggingAdapterForGwt( childLogger, childId);
    }



}
