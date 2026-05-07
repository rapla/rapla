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
package org.rapla.logger.internal;

import org.rapla.logger.Logger;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;

import jakarta.inject.Provider;

@SuppressWarnings("restriction")

public class Slf4jAdapter implements Provider<Logger>
{
    static final public int TRACE_INT = 00;
    static final public int DEBUG_INT = 10;
    static final public int INFO_INT = 20;
    static final public int WARN_INT = 30;
    static final public int ERROR_INT = 40;
    static ILoggerFactory iLoggerFactory;

    static public Logger getLoggerForCategory(String categoryName)
    {
        if (iLoggerFactory == null)
        {
            iLoggerFactory = createFactory();
        }
        LocationAwareLogger loggerForCategory = (LocationAwareLogger) iLoggerFactory.getLogger(categoryName);
        return new Wrapper(loggerForCategory, categoryName);
    }

    private static ILoggerFactory createFactory()
    {
        iLoggerFactory = LoggerFactory.getILoggerFactory();
        if (iLoggerFactory.getClass().getName().equalsIgnoreCase("org.slf4j.impl.SimpleLoggerFactory"))
        {
            try
            {
                iLoggerFactory = (ILoggerFactory) Slf4jAdapter.class.getClassLoader().loadClass("ch.qos.logback.classic.LoggerContext").newInstance();
            }
            catch (Exception e)
            {
                iLoggerFactory.getLogger("bootstrap").error(e.getMessage(), e);
            }
        }
        return iLoggerFactory;
    }

    public Logger get()
    {
        return getLoggerForCategory("rapla");
    }

    static class Wrapper implements Logger
    {
        LocationAwareLogger logger;
        String id;

        public Wrapper(LocationAwareLogger loggerForCategory, String id)
        {
            this.logger = loggerForCategory;
            this.id = id;
        }

        public Boolean isDebugEnabled()
        {
            return logger.isDebugEnabled();
        }

        public Boolean isTraceEnabled()
        {
            return logger.isTraceEnabled();
        }

        public Void trace(String message)
        {
            return log(TRACE_INT, message);
        }

        public Void debug(String message)
        {
            return log(DEBUG_INT, message);
        }

        public Void info(String message)
        {
            return log(INFO_INT, message);
        }

        private Void log(int infoInt, String message)
        {
            return log(infoInt, message, null);
        }

        private Void log(int level, String message, Throwable t)
        {
            Object[] argArray = null;
            String fqcn = Wrapper.class.getName();
            logger.log(null, fqcn, level, message, argArray, t);
            return null;
        }

        public Void warn(String message)
        {
            return log(WARN_INT, message);
        }

        public Void warn(String message, Throwable cause)
        {
            return log(WARN_INT, message, cause);
        }

        public Void error(String message)
        {
            return log(ERROR_INT, message);
        }

        public Void error(String message, Throwable cause)
        {
            return log(ERROR_INT, message, cause);
        }

        public Logger getChildLogger(String childLoggerName)
        {
            String childId = id + "." + childLoggerName;
            return getLoggerForCategory(childId);
        }

    }

}
