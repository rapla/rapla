package org.rapla.framework.logger;

public abstract class AbstractLogger implements Logger{
    
    protected int logLevel;
    public static final int LEVEL_FATAL = 4;
    public static final int LEVEL_ERROR = 3;
    public static final int LEVEL_WARN = 2;
    public static final int LEVEL_INFO = 1;
    public static final int LEVEL_DEBUG = 0;
    
    public AbstractLogger(int logLevel) {
        this.logLevel = logLevel;
    }

    public void error(String message, Throwable cause) {
        log( LEVEL_ERROR,message, cause);
    }

    private void log(int logLevel, String message) {
        log( logLevel, message, null);
    }

    private void log(int logLevel, String message, Throwable cause)
    {
        if ( logLevel < this.logLevel)
        {
            return;
        }
        write( logLevel, message, cause);
    }

    protected abstract void write(int logLevel, String message, Throwable cause);

    public void debug(String message) {
        log( LEVEL_DEBUG,message);  
    }
    
    public void info(String message) {
        log( LEVEL_INFO,message);         
    }

    public void warn(String message) {
        log( LEVEL_WARN,message); 
    }
    
    public void warn(String message, Throwable cause) {
        log( LEVEL_WARN,message, cause); 
    }

    public void error(String message) {
        log( LEVEL_ERROR,message); 
    }
  
    
    public void fatalError(String message) {
        log( LEVEL_FATAL,message); 
    }

    public void fatalError(String message, Throwable cause) {
        log( LEVEL_FATAL,message, cause); 
    }

   
    public boolean isDebugEnabled() {
        return logLevel<= LEVEL_DEBUG;
    }

    

    
}
