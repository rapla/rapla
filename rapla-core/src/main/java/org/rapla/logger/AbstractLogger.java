package org.rapla.logger;

public abstract class AbstractLogger implements Logger{
    
    protected int logLevel;
    public static final int LEVEL_FATAL = 4;
    public static final int LEVEL_ERROR = 3;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_DEBUG = 0;
    public static final int LEVEL_TRACE = -1;
    
    public AbstractLogger(int logLevel) {
        this.logLevel = logLevel;
    }

    public Void error(String message, Throwable cause) {
        return log( LEVEL_ERROR,message, cause);
    }

    private Void log(int logLevel, String message) {
        return log( logLevel, message, null);
    }

    private Void log(int logLevel, String message, Throwable cause)
    {
        Void result = null;
        if ( logLevel < this.logLevel)
        {
            return result;
        }
        write( logLevel, message, cause);
        return result;
    }

    protected abstract void write(int logLevel, String message, Throwable cause);

    public Void trace(String message) {
        return log( LEVEL_TRACE,message);
    }

    public Void debug(String message) {
        return log( LEVEL_DEBUG,message);
    }
    
    public Void info(String message) {
        return log( LEVEL_INFO,message);
    }

    public Void warn(String message) {
        return log( LEVEL_WARN,message);
    }
    
    public Void warn(String message, Throwable cause) {
        return log( LEVEL_WARN,message, cause);
    }

    public Void error(String message) {
        return log( LEVEL_ERROR,message);
    }


    public void fatalError(String message) {
        log( LEVEL_FATAL,message); 
    }

    public void fatalError(String message, Throwable cause) {
        log( LEVEL_FATAL,message, cause); 
    }

   
    public Boolean isDebugEnabled() {
        return logLevel<= LEVEL_DEBUG;
    }

    public Boolean isTraceEnabled() {
        return logLevel<= LEVEL_TRACE;
    }

    

    
}
