package org.rapla.bootstrap;

import java.io.IOException;


public class RaplaServerLoader
{
    public static void main(String[] args) throws IOException
    {
       RaplaJettyLoader.main(new String[] {"server"});
    }
}
