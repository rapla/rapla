package org.rapla.logger;

import javax.inject.Provider;
import javax.inject.Singleton;

import org.rapla.logger.internal.RaplaJDKLoggingAdapter;

@Singleton
public class RaplaBootstrapLogger implements Provider<Logger> {

    @Override public Logger get()
    {
        return createRaplaLogger();
    }

    public static Logger createRaplaLogger()
    {
        Logger logger;
        try {
            ClassLoader classLoader = RaplaJDKLoggingAdapter.class.getClassLoader();
            classLoader.loadClass("org.slf4j.Logger");
            @SuppressWarnings("unchecked")
            Provider<Logger> logManager = (Provider<Logger>) classLoader.loadClass("Slf4jAdapter").newInstance();
            logger = logManager.get();
            logger.info("Logging via SLF4J API.");
        } catch (Throwable e1) {
            Provider<Logger> logManager = new RaplaJDKLoggingAdapter( ); 
            logger = logManager.get();
            logger.info("Logging via java.util.logging API. " + e1.toString());
        }
        return logger;
    }

}
