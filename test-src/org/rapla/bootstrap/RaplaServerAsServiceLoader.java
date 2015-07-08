package org.rapla.bootstrap;

import java.io.File;
import java.io.IOException;


public class RaplaServerAsServiceLoader
{
    public static void main(String[] args) throws IOException
    {
		System.setProperty( "java.awt.headless", "true" );
    	String baseDir = System.getProperty("jetty.home");
    	if ( baseDir == null)
        {
    		baseDir = "../..";
    		System.setProperty( "jetty.home", new File(baseDir ).getCanonicalPath() );
    	}
        RaplaServerLoader.main( args);
    }
}
