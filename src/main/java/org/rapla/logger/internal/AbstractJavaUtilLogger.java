package org.rapla.logger.internal;

import java.util.logging.Level;
import java.util.logging.Logger;

abstract class AbstractJavaUtilLogger implements org.rapla.logger.Logger
{
    Logger logger;
    String id;

    public AbstractJavaUtilLogger(Logger logger, String id)
    {
        this.logger = logger;
        this.id = id;
    }

    public Boolean isTraceEnabled()
    {
        return logger.isLoggable(Level.FINEST);
    }

    public Boolean isDebugEnabled()
    {
        return logger.isLoggable(Level.CONFIG);
    }

    public Void trace(String message)
    {
        return log(Level.FINEST, message);
    }

    public Void debug(String message)
    {
        return log(Level.CONFIG, message);
    }

    public Void info(String message)
    {
        return log(Level.INFO, message);
    }

    public Void warn(String message)
    {
        return log(Level.WARNING, message);
    }

    public Void warn(String message, Throwable cause)
    {
        return log(Level.WARNING, message, cause);
    }

    public Void error(String message)
    {
        return log(Level.SEVERE, message);
    }

    public Void error(String message, Throwable cause)
    {
        return log(Level.SEVERE, message, cause);
    }

    private Void log(Level level, String message)
    {
        return log(level, message, null);
    }

    abstract protected Void log(Level level, String message, Throwable cause);


    public org.rapla.logger.Logger getChildLogger(String childLoggerName)
    {
        String childId = id + "." + childLoggerName;
        return createChildLogger(childId);
    }

    abstract protected org.rapla.logger.Logger createChildLogger(String childId);


}
