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

package org.rapla.components.calendarview;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.rapla.components.calendarview.swing.SwingBlock;
import org.rapla.components.calendarview.swing.SwingMonthView;
import org.rapla.components.calendarview.swing.SwingWeekView;
import org.rapla.components.calendarview.swing.ViewListener;

/** Test class for RaplaCalendar and RaplaTime */
public final class RaplaCalendarViewExample {
    private JTabbedPane tabbedPane = new JTabbedPane();
    JFrame frame;
    private List<MyAppointment> appointments = new ArrayList<MyAppointment>();
    
    public RaplaCalendarViewExample() {
        frame = new JFrame("Calendar test") {
            private static final long serialVersionUID = 1L;

            protected void processWindowEvent(WindowEvent e) {
                if (e.getID() == WindowEvent.WINDOW_CLOSING) {
                    dispose();
                    System.exit(0);
                } else {
                    super.processWindowEvent(e);
                }
            }
        };
        frame.setSize(700,550);

        JPanel testContainer = new JPanel();
        testContainer.setLayout(new BorderLayout());
        frame.getContentPane().add(testContainer);
        testContainer.add(tabbedPane,BorderLayout.CENTER);
        initAppointments();
        addWeekview();
        addDayview();
        addMonthview();
    }

    void initAppointments( ) {
        Calendar cal = Calendar.getInstance();
        // the first appointment
        cal.setTime( new Date());
        cal.set( Calendar.HOUR_OF_DAY, 12);
        cal.set( Calendar.MINUTE, 0);
        cal.set( Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        Date start = cal.getTime();
        cal.set( Calendar.HOUR_OF_DAY, 14);
        Date end = cal.getTime();
        appointments.add( new MyAppointment( start, end, "TEST" ));
        
        
        // the second appointment 
        cal.set( Calendar.DAY_OF_WEEK, Calendar.WEDNESDAY);
        cal.set( Calendar.HOUR_OF_DAY, 13);
        Date start2 = cal.getTime();
        cal.set( Calendar.HOUR_OF_DAY, 15);
        Date end2 = cal.getTime();
        appointments.add( new MyAppointment( start2, end2, "TEST2" ));
    }

    
    public void addWeekview()
    {
        final SwingWeekView wv = new SwingWeekView();
        tabbedPane.addTab("Weekview", wv.getComponent());
        Date today = new Date();
        // set to German locale
        wv.setLocale( Locale.GERMANY);
        // we exclude Saturday and Sunday
        List<Integer> excludeDays = new ArrayList<Integer>();
        excludeDays.add( new Integer(1));
        excludeDays.add( new Integer(7));
        wv.setExcludeDays( excludeDays );
        // Worktime is from 9 to 17 
        wv.setWorktime( 9, 17);
        // 1 row for 15 minutes
        wv.setRowsPerHour( 4 );
        // Set the size of a row to 15 pixel
        wv.setRowSize( 15 );
        //      set weekview date to Today
        wv.setToDate( today );
        
        // create blocks for today
        wv.addBuilder( new MyBuilder(  appointments ) );

        wv.rebuild();
        // Now we scroll to the first workhour
        wv.scrollToStart();
        
        wv.addCalendarViewListener( new MyCalendarListener(wv) );
        tabbedPane.addChangeListener( new ChangeListener() {
            public void stateChanged(ChangeEvent arg0) {
                wv.rebuild();
            }
        });
    }

    public void addDayview()
    {
        final SwingWeekView dv = new SwingWeekView();
        tabbedPane.addTab("Dayview", dv.getComponent());
        // set to German locale
        dv.setLocale( Locale.GERMANY);
        dv.setSlotSize( 300 );
        // we exclude everyday except the monday of the current week
        Set<Integer> excludeDays = new HashSet<Integer>();
        Calendar cal = Calendar.getInstance();
        cal.setTime ( new Date());
        cal.set( Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        Date mondayOfWeek = cal.getTime();
        for (int i=0;i<8;i++) {
            if ( i != cal.get( Calendar.DAY_OF_WEEK)) {
                excludeDays.add( new Integer(i));
            }
        }
        dv.setExcludeDays( excludeDays );
        // Worktime is from 9 to 17 
        dv.setWorktime( 9, 17);
        // 1 row for 15 minutes
        dv.setRowsPerHour( 4 );
        // Set the size of a row to 15 pixel
        dv.setRowSize( 15 );
        //      set weekview date to monday
        dv.setToDate( mondayOfWeek ) ;
        
        // create blocks for today
        dv.addBuilder( new MyBuilder( appointments ) );

        dv.rebuild();
        // Now we scroll to the first workhour
        dv.scrollToStart();
        
        dv.addCalendarViewListener( new MyCalendarListener(dv) );

        tabbedPane.addChangeListener( new ChangeListener() {
            public void stateChanged(ChangeEvent arg0) {
                dv.rebuild();
            }
        });
    }

    public void addMonthview()
    {
        final SwingMonthView mv = new SwingMonthView();
        tabbedPane.addTab("Monthview", mv.getComponent());
        Date today = new Date();
        // set to German locale
        mv.setLocale( Locale.GERMANY);
        // we exclude Saturday and Sunday
        List<Integer> excludeDays = new ArrayList<Integer>();
        excludeDays.add( new Integer(1));
        excludeDays.add( new Integer(7));
        mv.setExcludeDays( excludeDays );
        //      set weekview date to Today
        mv.setToDate( today );
        
        // create blocks for today
        mv.addBuilder( new MyBuilder(  appointments ) );

        mv.rebuild();
        
        mv.addCalendarViewListener( new MyMonthCalendarListener(mv) );

        tabbedPane.addChangeListener( new ChangeListener() {
            public void stateChanged(ChangeEvent arg0) {
                mv.rebuild();
            }
        });

    }
    

    public class MyAppointment {
        Date start;
        Date end;
        String label;

        public MyAppointment(Date start, Date end, String label) {
            this.start =  start;
            this.end = end;
            this.label = label;
        }
        
        public void move(Date newStart) {
            long diff = end.getTime() - start.getTime();
            start = new Date(newStart.getTime());
            end = new Date( newStart.getTime() + diff);
        }

        public void resize(Date newStart, Date newEnd) {
            if ( newStart != null )
            {
                this.start =  newStart;
            }
            if ( newEnd != null )
            {
                this.end = newEnd;
            }
        }

        public Date getStart() {
            return start;
        }

        public Date getEnd() {
            return end;
        }
        
        public String getLabel() {
            return label;
        }
    }


    static class MyBuilder implements Builder {
        final AbstractGroupStrategy strategy;
        List<Block> blocks;
        List<MyAppointment> appointments;
        boolean enable = true;
        
        MyBuilder(List<MyAppointment> appointments) {
            this.appointments = appointments;
            strategy = new BestFitStrategy();
            strategy.setResolveConflictsEnabled( true );
        }
         
        public void prepareBuild(Date startDate, Date endDate) {
            blocks = new ArrayList<Block>();
            for ( Iterator<MyAppointment> it = appointments.iterator(); it.hasNext(); )
            {
                MyAppointment appointment = it.next();
                if ( !appointment.getStart().before( startDate) && !appointment.getEnd().after( endDate ))
                {
                    blocks.add( new MyBlock(  appointment ));
                }
            }
        }

        public int getMaxMinutes() {
            return 24*60;
        }

        public int getMinMinutes() {
            return 0;
        }

        public void build(CalendarView cv) {
            strategy.build( cv, blocks);
        }

        public void setEnabled(boolean enable) {
            this.enable = enable;
        }

        public boolean isEnabled() {
            return enable;
        }
    
    }

    
    static class MyBlock implements SwingBlock {
        JLabel myBlockComponent;
        MyAppointment appointment;
        
        public MyBlock(MyAppointment appointment) {
            this.appointment = appointment;
            myBlockComponent = new JLabel();
            myBlockComponent.setText( appointment.getLabel() );
            myBlockComponent.setBorder( BorderFactory.createLineBorder( Color.BLACK));
            myBlockComponent.setBackground( Color.LIGHT_GRAY);
            myBlockComponent.setOpaque( true );
        }


        public Date getStart() {
            return appointment.getStart();
        }

        public Date getEnd() {
            return appointment.getEnd();
        }

        public MyAppointment getAppointment() {
            return appointment;
        }

        public Component getView() {
            return myBlockComponent;
        }

        public void paintDragging(Graphics g, int width, int height) {
           // If you comment out the following line, dragging displays correctly in month view
           myBlockComponent.setSize( width, height -1);
           myBlockComponent.paint( g );
        }

        public boolean isMovable() {
            return true;
        }

        public boolean isStartResizable() {
            return false;
        }

        public boolean isEndResizable() {
            return true;
        }


		public String getName() {
			return appointment.getLabel();
		}
    }
    

    
    static class MyCalendarListener implements ViewListener {
        CalendarView view;
        
        public MyCalendarListener(CalendarView view) {
            this.view = view;
        }
        
        public void selectionPopup(Component slotComponent, Point p, Date start, Date end, int slotNr) {
            System.out.println("Selection Popup in slot " + slotNr);
        }

        public void selectionChanged(Date start, Date end) {
            System.out.println("Selection change " + start + " - " + end );
        }

        public void blockPopup(Block block, Point p) {
            System.out.println("Block right click ");
        }

        public void blockEdit(Block block, Point p) {
            System.out.println("Block double click");
        }

        public void moved(Block block, Point p, Date newStart, int slotNr) {
            MyAppointment appointment = ((MyBlock) block).getAppointment();
            appointment.move( newStart);
            System.out.println("Block moved");
            view.rebuild();
        }

        public void resized(Block block, Point p, Date newStart, Date newEnd, int slotNr) {
            MyAppointment appointment = ((MyBlock) block).getAppointment();
            appointment.resize( newStart, newEnd);
            System.out.println("Block resized");
            view.rebuild();
        }

    }
    
    static class MyMonthCalendarListener extends MyCalendarListener {

        public MyMonthCalendarListener(CalendarView view) {
            super(view);
        }

        @Override
        public void moved(Block block, Point p, Date newStart, int slotNr) {
            MyAppointment appointment = ((MyBlock) block).getAppointment();
            Calendar cal = Calendar.getInstance();
            cal.setTime( appointment.getStart() );
            int hour = cal.get( Calendar.HOUR_OF_DAY);
            int minute = cal.get( Calendar.MINUTE);
            cal.setTime( newStart );
            cal.set( Calendar.HOUR_OF_DAY, hour);
            cal.set( Calendar.MINUTE, minute);
            appointment.move( cal.getTime());
            System.out.println("Block moved to " + cal.getTime());
            view.rebuild();
        }

    }

    public static void main(String[] args) {
        try {
            System.out.println("Testing RaplaCalendarView");
            RaplaCalendarViewExample example = new RaplaCalendarViewExample();
            example.frame.setVisible( true);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

}

