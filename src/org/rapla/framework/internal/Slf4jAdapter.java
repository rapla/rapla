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


import org.rapla.framework.Provider;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LocationAwareLogger;


@SuppressWarnings("restriction")
public class Slf4jAdapter implements Provider<org.rapla.framework.logger.Logger> {
    static ILoggerFactory logManager = LoggerFactory.getILoggerFactory();
    
    static public org.rapla.framework.logger.Logger getLoggerForCategory(String categoryName) {
    	LocationAwareLogger loggerForCategory = (LocationAwareLogger)logManager.getLogger(categoryName);
        return new Wrapper(loggerForCategory, categoryName);
    }

    public org.rapla.framework.logger.Logger get() {
        return getLoggerForCategory( "rapla");
    }
    
    static class Wrapper implements org.rapla.framework.logger.Logger{
        LocationAwareLogger logger;
        String id;

        public Wrapper( LocationAwareLogger loggerForCategory, String id) {
            this.logger = loggerForCategory;
            this.id = id;
        }

        public boolean isDebugEnabled() {
            return logger.isDebugEnabled();
        }

        public void debug(String message) {
            log(LocationAwareLogger.DEBUG_INT, message);
        }

		public void info(String message) {
            log( LocationAwareLogger.INFO_INT, message);
        }

        private void log(int infoInt, String message) {
        	log( infoInt, message, null);
		}

		private void log( int level, String message,Throwable t) {
			Object[] argArray = null;
			String fqcn = Wrapper.class.getName();
			logger.log(null, fqcn, level,message,argArray, t);
		}
        

        public void warn(String message) {
            log( LocationAwareLogger.WARN_INT, message);
        }

        public void warn(String message, Throwable cause) {
        	log( LocationAwareLogger.WARN_INT, message, cause);
        }

        public void error(String message) {
        	log( LocationAwareLogger.ERROR_INT, message);
        }

        public void error(String message, Throwable cause) {
        	log( LocationAwareLogger.ERROR_INT, message, cause);
        }

        public org.rapla.framework.logger.Logger getChildLogger(String childLoggerName) 
        {
            String childId = id + "." + childLoggerName;
            return getLoggerForCategory( childId);
        }
        
    }


}
