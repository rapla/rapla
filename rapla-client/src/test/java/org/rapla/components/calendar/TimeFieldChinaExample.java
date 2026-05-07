package org.rapla.components.calendar;

import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.event.WindowEvent;
import java.util.Locale;

public class TimeFieldChinaExample
{
    JFrame frame;

    public TimeFieldChinaExample()
    {
        frame = new JFrame( "Calendar test" )
        {
            private static final long serialVersionUID = 1L;

            protected void processWindowEvent( WindowEvent e )
            {
                if ( e.getID() == WindowEvent.WINDOW_CLOSING )
                {
                    dispose();
                    System.exit( 0 );
                }
                else
                {
                    super.processWindowEvent( e );
                }
            }
        };
        frame.setSize( 150, 80 );

        JPanel testContainer = new JPanel();
        testContainer.setLayout( new BorderLayout() );
        frame.getContentPane().add( testContainer );
        RaplaTime timeField = new RaplaTime();

        testContainer.add( timeField, BorderLayout.CENTER );

    }

    public void start()
    {
        frame.setVisible( true );
    }

    public static void main( String[] args )
    {
        try
        {
            Locale locale = 
                Locale.CHINA
                ;
            Locale.setDefault( locale );

            System.out.println( "Testing TimeField in locale " + locale );
            TimeFieldChinaExample example = new TimeFieldChinaExample();
            example.start();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            System.exit( 1 );
        }
    }

}
