package org.rapla;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;



@SuppressWarnings("restriction")
public class RaplaTestLogManager {
	// TODO Make me test again
	List<String> messages = new ArrayList<String>();
	static ThreadLocal<RaplaTestLogManager> localManager = new ThreadLocal<RaplaTestLogManager>();
	
    public RaplaTestLogManager() {
		localManager.set( this);
		clearMessages();
	     LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
	     List<Logger> loggerList = lc.getLoggerList();
	     Appender<ILoggingEvent> appender = new ConsoleAppender<ILoggingEvent>()
    			 {
    		 @Override
    				protected void writeOut(ILoggingEvent event)
    						throws IOException {
    					super.writeOut(event);
    				}
    		 @Override
    				protected void append(ILoggingEvent eventObject) {
    					super.append(eventObject);
    				}
    			 };
	     for ( Logger logger:loggerList)
	     {
	   // 	System.out.println(logger.toString());
			logger.addAppender( appender);
	     }
	     appender.setContext( lc);
			appender.start();

	   
    }

    public void clearMessages() {
    	messages.clear();
	}
    
    static public List<String> getErrorMessages()
    {
    	return localManager.get().messages;
    }




}
