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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
/** A ComboBox like time chooser.
 *  It is localizable and it uses swing-components.
 *  <p>The combobox editor is a {@link TimeField}. If the ComboBox-Button
 *  is pressed a TimeSelectionList will drop down.</p>
 *  @author Christopher Kohlhaas
 */
public final class RaplaTime extends RaplaComboBox {
    private static final long serialVersionUID = 1L;
    
    protected TimeField m_timeField;
    protected TimeList m_timeList;
    protected TimeModel m_timeModel;
    protected Collection<DateChangeListener> m_listenerList = new ArrayList<DateChangeListener>();
    private Date m_lastTime;
    private int m_visibleRowCount = -1;
    private int m_rowsPerHour = 4;
    private TimeRenderer m_renderer;
    private static Image clock;
    boolean m_showClock;
    
	/** Create a new TimeBox with the default locale. */
    public RaplaTime() {
        this(Locale.getDefault(),TimeZone.getDefault(),true, true);
    }

    /** Create a new TimeBox with the specified locale and timeZone. */
    public RaplaTime(Locale locale,TimeZone timeZone) {
        this(locale,timeZone,true, true);
    }

    /** Create a new TimeBox with the specified locale and timeZone.  The
        isDropDown flag specifies if times could be selected
        via a drop-down-box.
     */
    public RaplaTime(Locale locale,TimeZone timeZone,boolean isDropDown, boolean showClock) {
        super(isDropDown,new TimeField(locale,timeZone));
        m_showClock = showClock;
        m_timeModel = new TimeModel(locale, timeZone);
        m_timeField = (TimeField) m_editorComponent;
        Listener listener = new Listener();
        m_timeField.addChangeListener(listener);
        m_timeModel.addDateChangeListener(listener);
        m_lastTime = m_timeModel.getTime();
        
        if ( showClock ) 
        {
            if ( clock == null )
            {
                URL url = RaplaTime.class.getResource("clock.png");
                if ( url != null )
                {
                    clock =Toolkit.getDefaultToolkit().createImage(url );
                    MediaTracker m = new MediaTracker(this);
                    m.addImage(clock, 0);
                    try { m.waitForID(0); } catch (InterruptedException ex) {}
                }
            }

            getLabel().setIcon( new ImageIcon( createClockImage()));
            getLabel().setBorder( BorderFactory.createEmptyBorder(0,0,0,1));
        }

    }
    
    public TimeField getTimeField()
    {
    	return m_timeField;
    }

    static Color HOUR_POINTER = new Color( 40,40,100); 
    static Color MINUTE_POINTER = new Color( 100,100,180); 
    
    protected Image createClockImage() {
        BufferedImage image = new BufferedImage( 17, 17, BufferedImage.TYPE_INT_ARGB);
        Calendar calendar = Calendar.getInstance(getTimeZone(),m_timeModel.getLocale());
        calendar.setTime( m_timeModel.getTime());
        int hourOfDay  = calendar.get( Calendar.HOUR_OF_DAY) % 12; 
        int minute  = calendar.get( Calendar.MINUTE);
        
        Graphics g = image.getGraphics();
        
        double hourPos = (hourOfDay * 60 + minute  - 180) / (60.0 * 12)  * 2 * Math.PI ;
        double minutePos = (minute -15) / 60.0 * 2 * Math.PI;
        int xhour = (int) (Math.cos( hourPos) * 4.5);
        int yhour = (int) (Math.sin( hourPos ) * 4.5 );
        int xminute = (int) (Math.cos( minutePos ) * 6.5 );
        int yminute = (int) (Math.sin( minutePos) * 6.5);
        g.drawImage( clock,0,0,17,17, null);
        g.setColor( HOUR_POINTER);
        int centerx = 8;
        int centery = 8;
        g.drawLine( centerx, centery, centerx + xhour,centery + yhour);
        g.setColor( MINUTE_POINTER);
        g.drawLine( centerx, centery, centerx + xminute,centery +yminute);
        return image;
    }
    /** The granularity of the selection rows:
     * <ul>
     * <li>1:  1 rows per hour =   1 Hour</li>
     * <li>2:  2 rows per hour = 1/2 Hour</li>
     * <li>2:  3 rows per hour = 20 Minutes</li>
     * <li>4:  4 rows per hour = 15 Minutes</li>
     * <li>6:  6 rows per hour = 10 Minutes</li>
     * <li>12: 12 rows per hour =  5 Minutes</li>
     * </ul>
     */
    public void setRowsPerHour(int rowsPerHour) {
        m_rowsPerHour = rowsPerHour;
        if (m_timeList != null) {
            throw new IllegalStateException("Property can only be set during initialization.");
        }
    }

    /** @see #setRowsPerHour */
    public int getRowsPerHour() {
        return m_rowsPerHour;
    }

    class Listener implements ChangeListener,DateChangeListener {
        // Implementation of ChangeListener
        public void stateChanged(ChangeEvent evt) {
            validateEditor();
        }

        public void dateChanged(DateChangeEvent evt) {
            closePopup();
            if (needSync())
                m_timeField.setTime(evt.getDate());
            if (m_lastTime == null || !m_lastTime.equals(evt.getDate()))
                fireTimeChanged(evt.getDate());
            m_lastTime = evt.getDate();
            if ( clock != null && m_showClock)
            {
                getLabel().setIcon( new ImageIcon( createClockImage()));
            }
        }
    }

    /** test if we need to synchronize the dateModel and the m_timeField*/
    private boolean needSync() {
        return (m_timeField.getTime() != null && !m_timeModel.sameTime(m_timeField.getTime()));
    }

    protected void validateEditor() {
        if (needSync())
            m_timeModel.setTime(m_timeField.getTime());
    }

    /** the number of visble rows in the drop-down menu.*/
    public void setVisibleRowCount(int count) {
        m_visibleRowCount = count;
        if (m_timeList != null)
            m_timeList.getList().setVisibleRowCount(count);
    }

    public void setFont(Font font) {
        super.setFont(font);
        // Method called during constructor?
        if (m_timeList == null || font == null)
            return;
        m_timeList.setFont(font);
    }

    public TimeZone getTimeZone() {
        return m_timeField.getTimeZone();
    }

    /** Set the time relative to the given timezone.
     * The date,month and year values will be ignored.
     */
    public void setTime(Date time) {
        m_timeModel.setTime(time);
    }
    
    public Date getDurationStart() 
    {
		return m_timeModel.getDurationStart();
	}

	public void setDurationStart(Date durationStart) 
	{
		m_timeModel.setDurationStart(durationStart);
		if ( m_timeList != null)
		{
			m_timeList.setModel(m_timeModel,m_timeField.getOutputFormat());
		}
	}

    /** Set the time relative to the given timezone.
     */
    public void setTime(int hour, int minute) {
        m_timeModel.setTime(hour,minute);
    }
    
    /** Parse this date with a calendar-object set to the correct
        time-zone to get the hour,minute and second. The
        date,month and year values should be ignored.
     */
    public Date getTime() {
        return m_timeModel.getTime();
    }

    protected void showPopup() {
        validateEditor();
        super.showPopup();
        m_timeList.selectTime(m_timeField.getTime());
    }

    /** registers new DateChangedListener for this component.
     *  An DateChangedEvent will be fired to every registered DateChangedListener
     *  when the a different time is selected.
     * @see DateChangeListener
     * @see DateChangeEvent
    */
    public void addDateChangeListener(DateChangeListener listener) {
        m_listenerList.add(listener);
    }

    /** removes a listener from this component.*/
    public void removeDateChangeListener(DateChangeListener listener) {
        m_listenerList.remove(listener);
    }

    public DateChangeListener[] getDateChangeListeners() {
        return m_listenerList.toArray(new DateChangeListener[]{});
    }

    protected void fireTimeChanged(Date date) {
        DateChangeListener[] listeners = getDateChangeListeners();
        if (listeners.length == 0)
            return;
        DateChangeEvent evt = new DateChangeEvent(this,date);
        for (int i = 0; i<listeners.length; i++) {
            listeners[i].dateChanged(evt);
        }
    }

    /** The popup-component will be created lazily.*/
    public JComponent getPopupComponent() {
        if (m_timeList == null) {
            m_timeList = new TimeList( m_rowsPerHour);
            m_timeList.setFont( getFont() );
            if (m_visibleRowCount>=0)
                m_timeList.getList().setVisibleRowCount(m_visibleRowCount);
        }
        m_timeList.setTimeRenderer( m_renderer );
        m_timeList.setModel(m_timeModel,m_timeField.getOutputFormat());
        return m_timeList;
    }

    public void setTimeRenderer(TimeRenderer renderer) {
        m_renderer = renderer;
    }

}


class TimeList extends JPanel implements MenuElement,MouseListener,MouseMotionListener {
    private static final long serialVersionUID = 1L;

    JScrollPane scrollPane = new JScrollPane();
    NavButton upButton = new NavButton('^',10);
    NavButton downButton = new NavButton('v',10);
    JList m_list;
    DateFormat m_format;
    TimeModel m_timeModel;
    int m_rowsPerHour = 4;
    int m_minutesPerRow = 60 / m_rowsPerHour;
    TimeRenderer m_renderer;

    private double getRowHeight() {
        return m_list.getVisibleRect().getHeight()/m_list.getVisibleRowCount();
    }
    
    public TimeList(int rowsPerHour) {
        super();
        this.setLayout( new BorderLayout());
        JPanel upPane = new JPanel();
        upPane.setLayout( new BorderLayout());
        upPane.add( upButton, BorderLayout.CENTER );
        JPanel downPane = new JPanel();
        downPane.setLayout( new BorderLayout());
        downPane.add( downButton, BorderLayout.CENTER );
        upButton.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                int direction = (int)- getRowHeight() * (m_list.getVisibleRowCount() -1);
                JScrollBar bar = scrollPane.getVerticalScrollBar();
                int value = Math.min( Math.max( 0, bar.getValue() + direction ), bar.getMaximum());
                scrollPane.getVerticalScrollBar().setValue( value );
            }
            
        });
        downButton.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                int direction = (int) getRowHeight() * (m_list.getVisibleRowCount() -1) ;
                JScrollBar bar = scrollPane.getVerticalScrollBar();
                int value = Math.min( Math.max( 0, bar.getValue() + direction ), bar.getMaximum());
                scrollPane.getVerticalScrollBar().setValue( value );
            }
            
        });
        
        /*
        upPane.addMouseListener( new Mover( -1));
        upButton.addMouseListener( new Mover( -1));
        downPane.addMouseListener( new Mover( 1));
        downButton.addMouseListener( new Mover( 1));
        */
        //upPane.setPreferredSize( new Dimension(0,0));
        //downPane.setPreferredSize( new Dimension(0,0));
        this.add(upPane, BorderLayout.NORTH);
        this.add( scrollPane, BorderLayout.CENTER);
        this.add( downPane, BorderLayout.SOUTH);
        scrollPane.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
        scrollPane.getVerticalScrollBar().setEnabled( false );
        scrollPane.getVerticalScrollBar().setSize( new Dimension(0,0));
        scrollPane.getVerticalScrollBar().setPreferredSize( new Dimension(0,0));
        scrollPane.getVerticalScrollBar().setMaximumSize( new Dimension(0,0));
        scrollPane.getVerticalScrollBar().setMinimumSize( new Dimension(0,0));
        m_rowsPerHour = rowsPerHour;
        m_minutesPerRow = 60 / m_rowsPerHour;
        //this.setLayout(new BorderLayout());
        m_list = new JList();
        scrollPane.setViewportView( m_list);
        m_list.setBackground(this.getBackground());
        //JScrollPane scrollPane = new JScrollPane(m_list, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        m_list.setVisibleRowCount(8);
        m_list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        m_list.addMouseListener(this);
        m_list.addMouseMotionListener(this);
        DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            public Component getListCellRendererComponent( JList list, Object value, int index, boolean isSelected, boolean cellHasFocus )
            {
                int hour = getHourForIndex( index );
                int minute = getMinuteForIndex( index );
                String string = (String) value;
                
                Component component = super.getListCellRendererComponent( list, string, index, isSelected,cellHasFocus );
                if ( m_renderer!= null && !isSelected && !cellHasFocus ) {
                    Color color = m_renderer.getBackgroundColor( hour, minute );
                    if ( color != null ) {
                        component.setBackground( color );
                    }
                }
                return component;
            }
        };
		setRenderer(cellRenderer);
    }

	@SuppressWarnings("unchecked")
	private void setRenderer(DefaultListCellRenderer cellRenderer) {
		m_list.setCellRenderer( cellRenderer);
	}

    @SuppressWarnings("unchecked")
	public void setModel(TimeModel model,DateFormat format) {
        m_timeModel = model;
        m_format = (DateFormat) format.clone();
        Calendar calendar = Calendar.getInstance(m_format.getTimeZone(),model.getLocale());
        DefaultListModel listModel = new DefaultListModel();
        for (int i=0;i<24 * m_rowsPerHour;i++) {
            int hour = i/m_rowsPerHour;
            int minute = (i%m_rowsPerHour) * m_minutesPerRow;
            calendar.setTimeInMillis(0);
			calendar.set(Calendar.HOUR_OF_DAY,hour );
			calendar.set(Calendar.MINUTE,minute);
            Date durationStart = m_timeModel.getDurationStart();
            String duration = "";
            if ( m_renderer != null && durationStart != null)
            {
                Date time = calendar.getTime();
                long millis = time.getTime() - durationStart.getTime();
                int durationInMinutes = (int) (millis / (1000 * 60));
				duration = m_renderer.getDurationString(durationInMinutes);
            }
            String timeWithoutDuration = m_format.format(calendar.getTime());
            String time = timeWithoutDuration;
            if ( duration != null)
            {
            	time += " " + duration;
            }
			listModel.addElement("  " + time + " ");
        }
        m_list.setModel(listModel);
        int pos = (int)getPreferredSize().getWidth()/2 - 5;
        upButton.setLeftPosition(  pos);
        downButton.setLeftPosition( pos );
    }

    public void setTimeRenderer(TimeRenderer renderer) {
        m_renderer = renderer;
    }

    public JList getList() {
        return m_list;
    }

    public void setFont(Font font) {
        super.setFont(font);
        if (m_list == null || font == null)
            return;
        m_list.setFont(font);
        int pos = (int)getPreferredSize().getWidth()/2 - 5;
        upButton.setLeftPosition(  pos);
        downButton.setLeftPosition( pos );
    }

    /** Implementation-specific. Should be private.*/
    public void mousePressed(MouseEvent e) {
            ok();
    }
    /** Implementation-specific. Should be private.*/
    public void mouseClicked(MouseEvent e) {
    }
    /** Implementation-specific. Should be private.*/
    public void mouseReleased(MouseEvent e) {
    }
    /** Implementation-specific. Should be private.*/
    public void mouseEntered(MouseEvent me) {
    }
    /** Implementation-specific. Should be private.*/
    public void mouseExited(MouseEvent me) {
    }


    private int lastIndex = -1;
    private int lastY= -1;
    public void mouseDragged(MouseEvent e) {
    }
    public void mouseMoved(MouseEvent e) {
        if (e.getY() == lastY)
            return;
        lastY = e.getY();
        Point p = new Point(e.getX(),e.getY());
        int index = m_list.locationToIndex(p);
        if (index == lastIndex)
            return;
        lastIndex = index;
        m_list.setSelectedIndex(index);
    }

    public void selectTime(Date time) {
        Calendar calendar = Calendar.getInstance(m_timeModel.getTimeZone(),m_timeModel.getLocale());
        calendar.setTime(time);
        int index = (calendar.get(Calendar.HOUR_OF_DAY))  * m_rowsPerHour
            + (calendar.get(Calendar.MINUTE) / m_minutesPerRow);
        select(index);
    }


    private void select(int index) {
        m_list.setSelectedIndex(index);
        m_list.ensureIndexIsVisible(Math.max(index -3,0));
        m_list.ensureIndexIsVisible(Math.min(index + 3,m_list.getModel().getSize() -1));
    }


    // Start of MenuElement implementation
    public Component getComponent() {
        return this;
    }
    public MenuElement[] getSubElements() {
        return new MenuElement[0];
    }

    public void menuSelectionChanged(boolean isIncluded) {
    }

    public void processKeyEvent(KeyEvent event, MenuElement[] path, MenuSelectionManager manager) {
        int index;
        if (event.getID() == KeyEvent.KEY_PRESSED) {
            switch (event.getKeyCode()) {
            case (KeyEvent.VK_KP_UP):
            case (KeyEvent.VK_UP):
                index = m_list.getSelectedIndex();
                if (index > 0)
                    select(index - 1);
                break;
            case (KeyEvent.VK_KP_DOWN):
            case (KeyEvent.VK_DOWN):
                index = m_list.getSelectedIndex();
                if (index <m_list.getModel().getSize()-1)
                    select(index + 1);
                break;
            case (KeyEvent.VK_SPACE):
            case (KeyEvent.VK_ENTER):
                ok();
                break;
            case (KeyEvent.VK_ESCAPE):
                manager.clearSelectedPath();
                break;
            }
            //      System.out.println(event.getKeyCode());
            event.consume();
        }
    }

    private int getHourForIndex( int index ) {
        return index / m_rowsPerHour;
    }

    private int getMinuteForIndex( int index ) {
        return (index % m_rowsPerHour) * m_minutesPerRow;

    }

    private void ok() {
        int index = m_list.getSelectedIndex();
        int hour = getHourForIndex( index );
        int minute = getMinuteForIndex( index );
        Calendar calendar = Calendar.getInstance(m_timeModel.getTimeZone(),m_timeModel.getLocale());
        if (hour >= 0) {
            calendar.set(Calendar.HOUR_OF_DAY,hour );
            calendar.set(Calendar.MINUTE,minute);
            calendar.set(Calendar.SECOND,0);
            calendar.set(Calendar.MILLISECOND,0);
            m_timeModel.setTime(calendar.getTime());
        }
    }

    public void processMouseEvent(MouseEvent event, MenuElement[] path, MenuSelectionManager manager) {
    }
    // End of MenuElement implementation
    
}


