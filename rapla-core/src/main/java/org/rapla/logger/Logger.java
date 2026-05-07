package org.rapla.logger;


public interface Logger {
    Boolean isTraceEnabled();
    Boolean isDebugEnabled();
    Void debug(String message);
    Void info(String message);
    Void warn(String message);
    Void warn(String message, Throwable cause);
    Void error(String message);
    Void error(String message, Throwable cause);
    Void trace(String message);
    Logger getChildLogger(String childLoggerName);

}
