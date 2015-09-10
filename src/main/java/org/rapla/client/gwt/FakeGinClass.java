package org.rapla.client.gwt;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;

/**
 * Created by Christopher on 10.09.2015.
 */
public class FakeGinClass
{
    static
    {
        try
        {
            PrintWriter asd = new PrintWriter(new FileOutputStream(new File("D:/asd2.log")));
            asd.write("asd ");
            final Class<?> aClass = Class.forName("org.rapla.client.ActivityPresenterModule");
            aClass.newInstance();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        System.err.println("Klasse geladen");
        if(true)
        throw new IllegalStateException("Bla");
    }


}
