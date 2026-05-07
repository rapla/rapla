package org.rapla.logger;

public class ConsoleLogger extends AbstractLogger {

    String prefix;
    public ConsoleLogger(int logLevel) 
    {
        super(logLevel);
    }
    
    public ConsoleLogger(String prefix,int logLevel) 
    {
        super(logLevel);
        this.prefix = prefix;
    }

    public ConsoleLogger() 
    {
        this(LEVEL_INFO);
    }

    public Logger getChildLogger(String prefix) 
    {
        String newPrefix = prefix;
        if ( this.prefix != null)
        {
            newPrefix = this.prefix + "." + prefix;
        }
        return new ConsoleLogger( newPrefix, logLevel);
    }

    String getLogLevelString(int logLevel)
    {
        switch (logLevel)
        {
            case LEVEL_TRACE: return "TRACE";
            case LEVEL_DEBUG: return "DEBUG";
            case LEVEL_INFO: return "INFO";
            case LEVEL_WARN: return "WARN";
            case LEVEL_ERROR: return "ERROR";
            case LEVEL_FATAL: return "FATAL";
        }
        return "";
    }
    protected void write(int logLevel, String message, Throwable cause) 
    {
        StringBuffer buf = new StringBuffer();
        if ( prefix != null)
        {
            buf.append( prefix);
            buf.append( ": " );
        }
        if ( message != null)
        {
            buf.append( message);
            if ( cause != null)
            {
            	buf.append( ": " );
            }
        }
        while( cause!= null)
        {
            StackTraceElement[] stackTrace = cause.getStackTrace();
            buf.append( cause.getMessage());
            buf.append( "\n" );
            for ( StackTraceElement element:stackTrace)
            {
                buf.append("                         ");
                buf.append( element.toString());
                buf.append( "\n");
            }
            cause = cause.getCause();
            if  ( cause != null)
            {
                buf.append(" caused by ");
                buf.append( cause.getMessage());
                buf.append( "  " );
                
            }
        }
        writeln(logLevel,buf);
    }

    protected void writeln(int logLevel,StringBuffer buffer)
    {
        String logLevelString = getLogLevelString( logLevel );
        buffer.insert(0, " " );
        buffer.insert(0, logLevelString );
        String message= buffer.toString();
        System.out.println(message);
    }

}
