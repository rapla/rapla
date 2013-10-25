package org.rapla.framework.logger;


public interface Logger {
    boolean isDebugEnabled();
    void debug(String message);
    void info(String message);
    void warn(String message);
    void warn(String message, Throwable cause);
    void error(String message);
    void error(String message, Throwable cause);
    
    Logger getChildLogger(String childLoggerName);

}
