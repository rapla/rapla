/*--------------------------------------------------------------------------*
 | Copyright (C) 2014 Christopher Kohlhaas                                  |
 |                                                                          |
 | This program is free software; you can redistribute it and/or modify     |
 | it under the terms of the GNU General Public License as published by the |
 | Free Software Foundation. A copy of the license has been included with   |
 | these distribution in the COPYING file, if not go to www.fsf.org         |
 |                                                                          |
 | As a special exception, you are granted the permissions to link this     |
 | program with every library, which license fulfills the Open Source       |
 | Definition as published by the Open Source Initiative (OSI).             |
 *--------------------------------------------------------------------------*/

package org.rapla.components.calendar;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowEvent;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Test class for RaplaCalendar and RaplaTime */
public final class RaplaCalendarExample
{
    /** TextField for displaying the selected date, time*/
    private JTextField textField = new JTextField( 20 );
    private JTabbedPane tabbedPane = new JTabbedPane();
    /** Listener for all {@link DateChangeEvent}s. */
    DateChangeListener listener;
    JFrame frame;
    /** Stores all the raplaCalendar objects */
    ArrayList<RaplaCalendar> raplaCalendars = new ArrayList<RaplaCalendar>();
    /** Stores all the raplaTimes objects */
    ArrayList<RaplaTime> raplaTimes = new ArrayList<RaplaTime>();

    public RaplaCalendarExample()
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
        frame.setSize( 550, 450 );

        JPanel testContainer = new JPanel();
        testContainer.setLayout( new BorderLayout() );
        frame.getContentPane().add( testContainer );
        testContainer.add( tabbedPane, BorderLayout.CENTER );
        testContainer.add( textField, BorderLayout.SOUTH );

        listener = new DateChangeListener()
        {
            boolean listenerEnabled = true;

            public void dateChanged( DateChangeEvent evt )
            {
                // find matching RaplaTime and RaplaCalendar
                int index = raplaCalendars.indexOf( evt.getSource() );
                if ( index < 0 )
                    index = raplaTimes.indexOf( evt.getSource() );
                Date date = (  raplaCalendars.get( index ) ).getDate();
                Date time = ( raplaTimes.get( index ) ).getTime();
                TimeZone timeZone = (  raplaCalendars.get( index ) ).getTimeZone();

                Date dateTime = toDateTime( date, time, timeZone );

                DateFormat format = DateFormat.getDateTimeInstance();
                format.setTimeZone( TimeZone.getTimeZone( "GMT" ) );
                textField.setText( "GMT Time: " + format.format( dateTime ) );

                // Update the other calendars
                // Disable listeners during update of all raplacalendars and raplatimes
                if ( !listenerEnabled )
                    return;
                listenerEnabled = false;

                try
                {
                    for ( int i = 0; i < raplaCalendars.size(); i++ )
                    {
                        if ( i == index )
                            continue;
                        ( raplaCalendars.get( i ) ).setDate( dateTime );
                        (  raplaTimes.get( i ) ).setTime( dateTime );
                    }
                }
                finally
                {
                    listenerEnabled = true;
                }
            }
        };

        addDefault();
        addDESmall();
        addUSMedium();
        addRULarge();
    }

    /** Uses the first date parameter for year, month, date information and
     the second for hour, minutes, second, millisecond information.*/
    private Date toDateTime( Date date, Date time, TimeZone timeZone )
    {
        Calendar cal1 = Calendar.getInstance( timeZone );
        Calendar cal2 = Calendar.getInstance( timeZone );
        cal1.setTime( date );
        cal2.setTime( time );
        cal1.set( Calendar.HOUR_OF_DAY, cal2.get( Calendar.HOUR_OF_DAY ) );
        cal1.set( Calendar.MINUTE, cal2.get( Calendar.MINUTE ) );
        cal1.set( Calendar.SECOND, cal2.get( Calendar.SECOND ) );
        cal1.set( Calendar.MILLISECOND, cal2.get( Calendar.MILLISECOND ) );
        return cal1.getTime();
    }

    public void start()
    {
        frame.setVisible( true );
    }

    public void addDefault()
    {
        RaplaCalendar testCalendar = new RaplaCalendar();
        RaplaTime timeField = new RaplaTime();
        RaplaNumber numberField = new RaplaNumber( new Long( 0 ), new Long( 0 ), new Long( 60 ), false );

        testCalendar.addDateChangeListener( listener );
        timeField.addDateChangeListener( listener );

        JPanel testPanel = new JPanel();
        testPanel.setLayout( new FlowLayout() );
        testPanel.add( testCalendar );
        testPanel.add( timeField );
        testPanel.add( numberField );
        tabbedPane.addTab( "default '" + TimeZone.getDefault().getDisplayName() + "'", testPanel );

        raplaTimes.add( timeField );
        raplaCalendars.add( testCalendar );
    }

    public void addDESmall()
    {
        Locale locale = Locale.GERMANY;
        TimeZone timeZone = TimeZone.getTimeZone( "Europe/Berlin" );
        RaplaCalendar testCalendar = new RaplaCalendar( locale, timeZone );
        RaplaTime timeField = new RaplaTime( locale, timeZone );
        RaplaNumber numberField = new RaplaNumber( new Long( 0 ), new Long( 0 ), new Long( 60 ), false );
        Font font = new Font( "SansSerif", Font.PLAIN, 9 );

        testCalendar.setFont( font );
        // We want to highlight the 3. of october "Tag der deutschen Einheit".
        testCalendar.setDateRenderer( new WeekendHighlightRenderer()
        {
            public RenderingInfo getRenderingInfo( int dayOfWeek, int day, int month, int year )
            {
                if ( day == 3 && month == Calendar.OCTOBER )
                    return new RenderingInfo(Color.green, null,"Tag der deutschen Einheit") ;
                return super.getRenderingInfo( dayOfWeek, day, month, year );
            }
        } );
        testCalendar.addDateChangeListener( listener );
        timeField.setFont( font );
        timeField.addDateChangeListener( listener );
        numberField.setFont( font );

        JPanel testPanel = new JPanel();
        testPanel.setLayout( new FlowLayout() );
        testPanel.add( testCalendar );
        testPanel.add( timeField );
        testPanel.add( numberField );
        tabbedPane.addTab( "DE 'Europe/Berlin'", testPanel );

        raplaTimes.add( timeField );
        raplaCalendars.add( testCalendar );
    }

    public void addUSMedium()
    {
        Locale locale = Locale.US;
        TimeZone timeZone = TimeZone.getTimeZone( "GMT+8" );
        RaplaCalendar testCalendar = new RaplaCalendar( locale, timeZone );
        RaplaTime timeField = new RaplaTime( locale, timeZone );
        RaplaNumber numberField = new RaplaNumber( new Long( 0 ), new Long( 0 ), new Long( 60 ), false );
        Font font = new Font( "Serif", Font.PLAIN, 18 );

        testCalendar.setFont( font );
        // We want to highlight the 4. of july "Independence Day".
        testCalendar.setDateRenderer( new WeekendHighlightRenderer()
        {
            
            public RenderingInfo getRenderingInfo( int dayOfWeek, int day, int month, int year )
            {
                if ( day == 4 && month == Calendar.JULY )
                    return new RenderingInfo(Color.red, null,"Independence Day") ;
                return super.getRenderingInfo( dayOfWeek, day, month, year );
            }

        } );
        testCalendar.addDateChangeListener( listener );
        numberField.setFont( font );
        timeField.setFont( font );
        timeField.addDateChangeListener( listener );

        JPanel testPanel = new JPanel();
        testPanel.setLayout( new FlowLayout() );
        testPanel.add( testCalendar );
        testPanel.add( timeField );
        testPanel.add( numberField );
        tabbedPane.addTab( "US 'GMT+8'", testPanel );

        raplaTimes.add( timeField );
        raplaCalendars.add( testCalendar );
    }

    public void addRULarge()
    {
        Locale locale = new Locale( "ru", "RU" );
        TimeZone timeZone = TimeZone.getTimeZone( "GMT-6" );
        RaplaCalendar testCalendar = new RaplaCalendar( locale, timeZone, false );
        RaplaTime timeField = new RaplaTime( locale, timeZone );
        Font font = new Font( "Arial", Font.BOLD, 26 );

        testCalendar.setFont( font );
        // Only highlight sunday.
        WeekendHighlightRenderer renderer = new WeekendHighlightRenderer();
        renderer.setHighlight( Calendar.SATURDAY, false );
        testCalendar.setDateRenderer( renderer );
        testCalendar.addDateChangeListener( listener );

        timeField.setFont( font );
        timeField.addDateChangeListener( listener );

        JPanel testPanel = new JPanel();
        testPanel.setLayout( new FlowLayout() );
        testPanel.add( testCalendar.getPopupComponent() );
        testPanel.add( timeField );
        tabbedPane.addTab( "RU 'GMT-6'", testPanel );

        raplaTimes.add( timeField );
        raplaCalendars.add( testCalendar );
    }

    public static void main( String[] args )
    {
        try
        {
            System.out.println( "Testing RaplaCalendar" );
            RaplaCalendarExample example = new RaplaCalendarExample();
            example.start();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            System.exit( 1 );
        }
    }

}
