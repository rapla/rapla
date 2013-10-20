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
package org.rapla.framework.internal;


import java.util.logging.Level;
import java.util.logging.Logger;

import org.rapla.framework.Provider;


public class RaplaJDKLoggingAdapter implements Provider<org.rapla.framework.logger.Logger> {

    public org.rapla.framework.logger.Logger get() {
        final Logger logger = getLogger(  "rapla");
        return new Wrapper(logger, "rapla");
	}
   
	static private java.util.logging.Logger getLogger(String categoryName) 
	{
		Logger logger = Logger.getLogger(categoryName);
		return logger;
	}

    static String WRAPPER_NAME = RaplaJDKLoggingAdapter.class.getName();

    class Wrapper implements org.rapla.framework.logger.Logger{
        java.util.logging.Logger logger;
        String id;

        public Wrapper( java.util.logging.Logger logger, String id) {
            this.logger = logger;
            this.id = id;
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
			StackTraceElement[] stackTrace = new Throwable().getStackTrace();
	        String sourceClass = null;
	        String sourceMethod =null;
			for (StackTraceElement element:stackTrace)
            {
                String classname = element.getClassName();
                if ( !classname.startsWith(WRAPPER_NAME))
                {
                    sourceClass=classname;
                    sourceMethod =element.getMethodName();
                    break;
                }
            }
            logger.logp(level,sourceClass, sourceMethod,message, cause);
		}

        public org.rapla.framework.logger.Logger getChildLogger(String childLoggerName) 
        {
            String childId = id+ "." + childLoggerName;
            Logger childLogger = getLogger( childId);
            return new Wrapper( childLogger, childId);
        }
    }

}
