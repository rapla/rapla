package org.rapla.bootstrap;

import java.io.File;
import java.io.IOException;


public class RaplaJettyLoader 
{
    public static void main(String[] args) throws IOException
    {
    	String baseDir = System.getProperty("jetty.home");
    	if ( baseDir == null)
        {
    		baseDir = ".";
			if ( new File("./src/test").exists())
			{
				baseDir = "./src/test";
			}
			System.setProperty( "jetty.home", new File(baseDir ).getCanonicalPath() );
    	}
    	for ( String arg:args)
    	{
    		if ( arg != null && arg.equalsIgnoreCase("server"))
    		{
    			System.setProperty( "java.awt.headless", "true" );
    		}
    	}
    	String dirList = "lib,lib/logging,lib/ext,resources";
        String classname = "org.rapla.bootstrap.CustomJettyStarter";
        String methodName = "main";
        //System.setProperty( "java.awt.headless", "true" );
        String test = "Starting rapla from " + System.getProperty("jetty.home");
        System.out.println(test );
        RaplaLoader.start( baseDir,dirList, classname,methodName, new Object[] {args });
    }
    
    

}
