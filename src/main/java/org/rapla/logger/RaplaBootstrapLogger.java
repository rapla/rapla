package org.rapla.logger;

import org.rapla.logger.internal.JavaUtilLoggingAdapter;

import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton public class RaplaBootstrapLogger implements Provider<Logger>
{

    public static Logger createRaplaLogger()
    {
        return new RaplaBootstrapLogger().get();
    }

    @Override public Logger get()
    {
        Logger logger;
        try
        {
            logger = logViaSlf4j();
        }
        catch (Throwable e1)
        {
            try
            {
                logger = logViaLog4j();
            }
            catch (Throwable e2)
            {
                logger = logViaJavaUtilLogging();
            }
        }
        return logger;
    }

    private  Logger logViaSlf4j() throws ClassNotFoundException, InstantiationException, IllegalAccessException
    {
        Logger logger;
        ClassLoader classLoader = JavaUtilLoggingAdapter.class.getClassLoader();
        classLoader.loadClass("org.slf4j.Logger");
        final Class<?> aClass = classLoader.loadClass("org.rapla.logger.internal.Slf4jAdapter");
        @SuppressWarnings("unchecked") Provider<Logger> logManager = (Provider<Logger>) aClass.newInstance();
        logger = logManager.get();
        logger.info("Logging via SLF4J API.");
        return logger;
    }

    private  Logger logViaLog4j() throws ClassNotFoundException, InstantiationException, IllegalAccessException
    {
        Logger logger;
        ClassLoader classLoader = JavaUtilLoggingAdapter.class.getClassLoader();
        classLoader.loadClass("org.apache.logging.log4j.Logger");
        final Class<?> aClass = classLoader.loadClass("org.rapla.logger.internal.Log4jAdapter");
        @SuppressWarnings("unchecked") Provider<Logger> logManager = (Provider<Logger>) aClass.newInstance();
        logger = logManager.get();
        logger.info("Logging via Log4j API.");
        return logger;
    }

    private  Logger logViaJavaUtilLogging()
    {
        Logger logger;Provider<Logger> logManager = new JavaUtilLoggingAdapter();
        logger = logManager.get();
        logger.info("Logging via java.util.logging API. " );
        return logger;
    }

}
