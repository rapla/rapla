package org.rapla.bootstrap;

import java.io.IOException;


public class RaplaServerLoader
{
    public static void main(String[] args) throws IOException
    {
       System.setProperty( "java.awt.headless", "true" );
       RaplaJettyLoader.main(new String[] {"server"});
    }
}
