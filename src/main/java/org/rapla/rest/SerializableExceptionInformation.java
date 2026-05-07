package org.rapla.rest;

import java.util.ArrayList;
import java.util.List;

public class SerializableExceptionInformation
{

    public static class SerializableExceptionStacktraceInformation
    {
        private String className;
        private String methodName;
        private int lineNumber;
        private String fileName;

        public SerializableExceptionStacktraceInformation()
        {
        }

        public SerializableExceptionStacktraceInformation(String className, String methodName, int lineNumber, String fileName)
        {
            super();
            this.className = className;
            this.methodName = methodName;
            this.lineNumber = lineNumber;
            this.fileName = fileName;
        }

        public SerializableExceptionStacktraceInformation(StackTraceElement stackTraceElement)
        {
            className = stackTraceElement.getClassName();
            methodName = stackTraceElement.getMethodName();
            lineNumber = stackTraceElement.getLineNumber();
            fileName = stackTraceElement.getFileName();
        }

        public String getClassName()
        {
            return className;
        }

        public String getMethodName()
        {
            return methodName;
        }

        public int getLineNumber()
        {
            return lineNumber;
        }

        public String getFileName()
        {
            return fileName;
        }
    }

    private String message;
    private String exceptionClass;
    private List<SerializableExceptionStacktraceInformation> stacktrace;

    public SerializableExceptionInformation()
    {
    }

    @Override
    public String toString()
    {
        return "SerializableExceptionInformation{" + "message='" + message + '\'' + ", exceptionClass='" + exceptionClass + '\'' + '}';
    }

    public SerializableExceptionInformation(Throwable t)
    {
        this.message = t.getMessage();
        this.stacktrace = new ArrayList<>();
        this.exceptionClass = t.getClass().getCanonicalName();
        final StackTraceElement[] stackTrace2 = t.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace2)
        {
            stacktrace.add(new SerializableExceptionStacktraceInformation(stackTraceElement));
        }
    }

    public SerializableExceptionInformation(String message, String exceptionClass, List<SerializableExceptionStacktraceInformation> stacktrace)
    {
        this.message = message;
        this.exceptionClass = exceptionClass;
        this.stacktrace = stacktrace;
    }

    public String getExceptionClass()
    {
        return exceptionClass;
    }

    public String getMessage()
    {
        return message;
    }

    public List<SerializableExceptionStacktraceInformation> getStacktrace()
    {
        return stacktrace;
    }

}
