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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.MenuElement;
import javax.swing.MenuSelectionManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
/** The graphical date-selection field with month and year incerement/decrement buttons.
 *  @author Christopher Kohlhaas
 */


public class CalendarMenu extends JPanel implements MenuElement {
    private static final long serialVersionUID = 1L;

    static Color BACKGROUND_COLOR = Color.white;
    static Border FOCUSBORDER = new LineBorder(DaySelection.FOCUSCOLOR,1);
    static Border EMPTYBORDER = new EmptyBorder(1,1,1,1);
    BorderLayout   borderLayout1 = new BorderLayout();

    JPanel         topSelection = new JPanel();
    Border           border1 = BorderFactory.createEtchedBorder();
    BorderLayout     borderLayout2 = new BorderLayout();
    JPanel           monthSelection = new JPanel();
    NavButton   jPrevMonth = new NavButton('<');
    JLabel             labelMonth    = new JLabel();
    NavButton   jNextMonth = new NavButton('>');
    JPanel           yearSelection = new JPanel();
    NavButton   jPrevYear = new NavButton('<');
    JLabel             labelYear   = new JLabel();
    NavButton   jNextYear = new NavButton('>');

    protected DaySelection  daySelection;
    JLabel  labelCurrentDay = new JLabel();
    JButton           focusButton = new JButton() {
        private static final long serialVersionUID = 1L;

            public void paint(Graphics g) {
                Dimension size = getSize();
                g.setColor(getBackground());
                g.fillRect(0,0,size.width,size.height);
            }
        };



    DateModel m_model;
    DateModel m_focusModel;

    String[] m_monthNames;
    boolean m_focusable = true;

    public CalendarMenu(DateModel newModel) {
        m_model = newModel;

        m_monthNames = createMonthNames();

        m_focusModel = new DateModel(newModel.getLocale(),newModel.getTimeZone());
        daySelection = new DaySelection(newModel,m_focusModel);

        initGUI();
        Listener listener = new Listener();

        jPrevMonth.addActionListener(listener);
        jPrevMonth.setBorder( null );
        jNextMonth.addActionListener(listener);
        jNextMonth.setBorder( null );
        jPrevYear.addActionListener(listener);
        jPrevYear.setBorder( null );
        jNextYear.addActionListener(listener);
        jNextYear.setBorder( null );

        jPrevMonth.setFocusable( false );
        jNextMonth.setFocusable( false );
        jPrevYear.setFocusable( false );
        jNextYear.setFocusable( false );

        this.addMouseListener(listener);
        jPrevMonth.addMouseListener(listener);
        jNextMonth.addMouseListener(listener);
        jPrevYear.addMouseListener(listener);
        jNextYear.addMouseListener(listener);
        labelCurrentDay.addMouseListener(listener);
        daySelection.addMouseListener(listener);
        labelCurrentDay.addMouseListener(listener);
        m_model.addDateChangeListener(listener);
        m_focusModel.addDateChangeListener(listener);
        focusButton.addKeyListener(listener);

        focusButton.addFocusListener(listener);
        calculateSizes();
        m_focusModel.setDate(m_model.getDate());
    }

    class Listener implements ActionListener,MouseListener,FocusListener,DateChangeListener,KeyListener {
        public void actionPerformed(ActionEvent evt) {
            if ( evt.getSource() == jNextMonth) {
                m_focusModel.addMonth(1);
            } // end of if ()

            if ( evt.getSource() == jPrevMonth) {
                m_focusModel.addMonth(-1);
            } // end of if ()

            if ( evt.getSource() == jNextYear) {
                m_focusModel.addYear(1);
            } // end of if ()

            if ( evt.getSource() == jPrevYear) {
                m_focusModel.addYear(-1);
            } // end of if ()
        }

        // Implementation of DateChangeListener
        public void dateChanged(DateChangeEvent evt) {
            if (evt.getSource() == m_model) {
                m_focusModel.setDate(evt.getDate());
            } else {
                updateFields();
            }
        }
        // Implementation of MouseListener
        public void mousePressed(MouseEvent me) {
            if (me.getSource() == labelCurrentDay) {
                // Set the current day as sellected day
                m_model.setDate(new Date());
            } else {
                if (m_focusable && !focusButton.hasFocus())
                    focusButton.requestFocus();
            }
        }
        public void mouseClicked(MouseEvent me) {
        }
        public void mouseReleased(MouseEvent me) {
        }
        public void mouseEntered(MouseEvent me) {
        }
        public void mouseExited(MouseEvent me) {
        }

        // Implementation of KeyListener
        public void keyPressed(KeyEvent e) {
            processCalendarKey(e);
        }
        public void keyReleased(KeyEvent e) {
        }
        public void keyTyped(KeyEvent e) {
        }

        // Implementation of FocusListener
        public void focusGained(FocusEvent e) {
            if (e.getSource() == focusButton)
                setBorder(FOCUSBORDER);
            else
                transferFocus();
        }
        public void focusLost(FocusEvent e) {
            if (e.getSource() == focusButton)
                setBorder(EMPTYBORDER);
        }
    }

    private void updateFields() {
        labelCurrentDay.setText(m_focusModel.getCurrentDateString());
        labelMonth.setText(m_monthNames[m_focusModel.getMonth() -1]);
        labelYear.setText(m_focusModel.getYearString());
    }

    public DateModel getModel() {
        return m_model;
    }

    public boolean hasFocus() {
        return focusButton.hasFocus();
    }

    public boolean isFocusable() {
        return m_focusable;
    }

    public void setFocusable(boolean m_focusable) {
        this.m_focusable = m_focusable;
    }

    // #TODO Property change listener for TimeZone
    public void setTimeZone(TimeZone timeZone) {
        m_focusModel.setTimeZone(timeZone);
    }

    public void setFont(Font font) {
        super.setFont(font);
        // Method called during constructor?
        if (labelMonth == null || font == null)
            return;
        labelMonth.setFont(font);
        labelYear.setFont(font);
        daySelection.setFont(font);
        labelCurrentDay.setFont(font);
        calculateSizes();
        invalidate();
    }

    public void requestFocus() {
        if (m_focusable)
            focusButton.requestFocus();
    }

    public DaySelection getDaySelection() {
        return daySelection;
    }

    private void calculateSizes() {
        // calculate max size of month names
        int maxWidth =0;
        FontMetrics fm = getFontMetrics(labelMonth.getFont());
        for (int i=0;i< m_monthNames.length;i++) {
            int len = fm.stringWidth(m_monthNames[i]);
            if (len>maxWidth)
                maxWidth = len;
        }
        labelMonth.setPreferredSize(new Dimension(maxWidth,fm.getHeight()));
        int h = fm.getHeight();
        jPrevMonth.setSize(h,h);
        jNextMonth.setSize(h,h);
        jPrevYear.setSize(h,h);
        jNextYear.setSize(h,h);
        // Workaraund for focus-bug in JDK 1.3.1
        Border dummyBorder = new EmptyBorder(fm.getHeight(),0,0,0);
        focusButton.setBorder(dummyBorder);
    }

    private void initGUI() {
        setBorder(EMPTYBORDER);
        topSelection.setLayout(borderLayout2);
        topSelection.setBorder(border1);
        daySelection.setBackground(BACKGROUND_COLOR);

        FlowLayout flowLayout = new FlowLayout(FlowLayout.LEFT,0,0);
        monthSelection.setLayout(flowLayout);
        monthSelection.add(focusButton);
        monthSelection.add(jPrevMonth);
        monthSelection.add(labelMonth);
        monthSelection.add(jNextMonth);

        yearSelection.setLayout(flowLayout);
        yearSelection.add(jPrevYear);
        yearSelection.add(labelYear);
        yearSelection.add(jNextYear);

        topSelection.add(monthSelection,BorderLayout.WEST);
        topSelection.add(yearSelection,BorderLayout.EAST);

        setLayout(borderLayout1);
        add(topSelection,BorderLayout.NORTH);
        add(daySelection,BorderLayout.CENTER);
        add(labelCurrentDay,BorderLayout.SOUTH);
    }

    private String[] createMonthNames( ) {
        Calendar calendar = Calendar.getInstance(m_model.getLocale());
        calendar.setLenient(true);
        Collection<String> monthNames = new ArrayList<String>();
        SimpleDateFormat format = new SimpleDateFormat("MMM",m_model.getLocale());
        int firstMonth = 0;
        int month = 0;
        while (true) {
            calendar.set(Calendar.DATE,1);
            calendar.set(Calendar.MONTH,month);
            if (month == 0)
                firstMonth = calendar.get(Calendar.MONTH);
            else
                if (calendar.get(Calendar.MONTH) == firstMonth)
                    break;
            monthNames.add(format.format(calendar.getTime()));
            month ++;
        }
        return monthNames.toArray(new String[0]);
    }

    private void processCalendarKey(KeyEvent e) {
        switch (e.getKeyCode()) {
        case (KeyEvent.VK_KP_UP):
        case (KeyEvent.VK_UP):
            m_focusModel.addDay(-7);
            break;
        case (KeyEvent.VK_KP_DOWN):
        case (KeyEvent.VK_DOWN):
            m_focusModel.addDay(7);
            break;
        case (KeyEvent.VK_KP_LEFT):
        case (KeyEvent.VK_LEFT):
            m_focusModel.addDay(-1);
            break;
        case (KeyEvent.VK_KP_RIGHT):
        case (KeyEvent.VK_RIGHT):
            m_focusModel.addDay(1);
            break;
        case (KeyEvent.VK_PAGE_DOWN):
            m_focusModel.addMonth(1);
            break;
        case (KeyEvent.VK_PAGE_UP):
            m_focusModel.addMonth(-1);
            break;
        case (KeyEvent.VK_ENTER):
            m_model.setDate(m_focusModel.getDate());
            break;
        case (KeyEvent.VK_SPACE):
            m_model.setDate(m_focusModel.getDate());
            break;
        }
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
        if (event.getID() == KeyEvent.KEY_PRESSED)
            processCalendarKey(event);
        switch (event.getKeyCode()) {
        case (KeyEvent.VK_ENTER):
        case (KeyEvent.VK_ESCAPE):
            manager.clearSelectedPath();
        }
        event.consume();
    }

    public void processMouseEvent(MouseEvent event, MenuElement[] path, MenuSelectionManager manager) {
    }
    // End of MenuElement implementation
}

