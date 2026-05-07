package org.rapla.logger;

import jakarta.inject.Singleton;

@Singleton
public class NullLogger extends AbstractLogger
{
    public NullLogger() {
        super(LEVEL_FATAL);
    }

    public Logger getChildLogger(String childLoggerName) {
        return new NullLogger();
    }

    protected void write(int logLevel, String message, Throwable cause) {
    }


}
