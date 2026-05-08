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


import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JavaUtilLoggingAdapter implements Supplier<org.rapla.logger.Logger>
{
    private static final String WRAPPER_NAME = JavaUtilLoggingAdapter.class.getName();
    private static final String ABSTRACTLOGGER_NAME = AbstractJavaUtilLogger.class.getName();
    private static final String JDKLOGGER_NAME = JDKLogger.class.getName();

    AbstractJavaUtilLogger abstractJavaUtilLogger;

    public org.rapla.logger.Logger get() {
        if ( abstractJavaUtilLogger == null)
        {
            synchronized ( this) {
                if ( abstractJavaUtilLogger == null)
                {
                    Logger logger = Logger.getLogger(  "rapla");
                    abstractJavaUtilLogger = new JDKLogger(logger, "rapla");
                }
            }
        }
        return abstractJavaUtilLogger;
    }

    private static void log_(Logger logger, Level level, String message, Throwable cause) {
        StackTraceElement[] stackTrace = new Throwable().getStackTrace();
        String sourceClass = null;
        String sourceMethod =null;
        for (StackTraceElement element:stackTrace)
        {
            String classname = element.getClassName();
            if ( !classname.contains(WRAPPER_NAME) && !classname.contains(ABSTRACTLOGGER_NAME)  && !classname.contains(JDKLOGGER_NAME))
            {
                sourceClass=classname;
                sourceMethod =element.getMethodName();
                break;
            }
        }
        logger.logp(level,sourceClass, sourceMethod,message, cause);
    }

    static protected class JDKLogger extends AbstractJavaUtilLogger
    {
        public JDKLogger(Logger logger, String rapla)
        {
            super(logger, rapla);
        }

        @Override
        protected Void log(Level level, String message, Throwable cause) {
            JavaUtilLoggingAdapter.log_(logger,level,message,cause);
            return null;
        }

        @Override protected org.rapla.logger.Logger createChildLogger(String childId)
        {
            Logger childLogger = Logger.getLogger(childId);
            return new JDKLogger( childLogger, childId);
        }


    }



}
