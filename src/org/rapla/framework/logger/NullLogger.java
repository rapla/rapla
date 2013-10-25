package org.rapla.framework.logger;

public class NullLogger extends AbstractLogger  {
    public NullLogger() {
        super(LEVEL_FATAL);
    }

    public Logger getChildLogger(String childLoggerName) {
        return this;
    }

    protected void write(int logLevel, String message, Throwable cause) {
    }


}
