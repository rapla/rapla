/*--------------------------------------------------------------------------*
 | Copyright (C) 2003 Christopher Kohlhaas                                  |
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

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
/** The RaplaNumber is an adapter for NumberField.
 * <strong>Warning!</strong> Currently only Longs are supported.
 * @see NumberField
 */
public final class RaplaNumber extends JPanel{
    private static final long serialVersionUID = 1L;

    NumberField m_numberField = null;
    public static Number ZERO = new Long(0);
    public static Number ONE = new Long(1);
    public static Number DEFAULT_STEP_SIZE = new Long(1);
    EventListenerList m_listenerList;
    Listener m_listener = new Listener();
    Number m_emptyValue = ZERO;
    JPanel m_buttonPanel = new JPanel();
    RaplaArrowButton m_upButton = new RaplaArrowButton('+', 15);//'^',10,false);
    RaplaArrowButton m_downButton = new RaplaArrowButton('-', 15);//new NavButton('v',10,false);

    /** currently only Longs are supported */
    public RaplaNumber() {
        this( null, null, null, false);
    }

    public RaplaNumber(Number value,Number minimum,Number maximum,boolean isNullPermitted) {
        m_numberField = new NumberField(minimum,maximum,DEFAULT_STEP_SIZE.intValue(),10);
        m_numberField.setNumber(value);
        m_numberField.setDisabledTextColor(Color.black);
        m_numberField.setNullPermitted(isNullPermitted);
        if (minimum != null && minimum.longValue()>0)
            m_emptyValue = minimum;
        else if (maximum != null && maximum.longValue()<0)
            m_emptyValue = maximum;
        m_buttonPanel.setLayout(new GridLayout(2,1));
        m_buttonPanel.add(m_upButton);
        m_upButton.setBorder( null);
        m_buttonPanel.add(m_downButton);
        m_downButton.setBorder(null);
        m_buttonPanel.setMinimumSize(new Dimension(18,20));
        m_buttonPanel.setPreferredSize(new Dimension(18,20));
        m_buttonPanel.setBorder(BorderFactory.createEtchedBorder());
        m_upButton.setClickRepeatDelay(50);
        m_downButton.setClickRepeatDelay(50);
        m_upButton.addActionListener(m_listener);
        m_downButton.addActionListener(m_listener);
        setLayout(new BorderLayout());
        add(m_numberField,BorderLayout.CENTER);
        add(m_buttonPanel,BorderLayout.EAST);

        m_upButton.setFocusable( false);
        m_downButton.setFocusable( false);
    }
    
    @Override
    public void setBackground(Color bg)
    {
        super.setBackground(bg);
        if(m_numberField != null)
        m_numberField.setBackground(bg);
    }
    
    @Override
    public void setForeground(Color fg)
    {
        super.setForeground(fg);
        if(m_numberField != null)
        m_numberField.setForeground(fg);
    }

    public void setFont(Font font) {
        super.setFont(font);
        // Method called during constructor?
        if (m_numberField == null || font == null)
            return;
        m_numberField.setFont(font);
    }

    public void setColumns(int columns) {
        m_numberField.setColumns(columns);
    }

    public NumberField getNumberField() {
        return m_numberField;
    }

    public void setEnabled( boolean enabled ) {
        super.setEnabled( enabled );
        if ( m_numberField != null ) {
            m_numberField.setEnabled( enabled );
        }
        if ( m_upButton != null ) {
            m_upButton.setEnabled ( enabled );
            m_downButton.setEnabled ( enabled );
        }
    }

    /** currently only Longs are supported */
    public void setNumber(Number newValue) {
        m_numberField.setNumber(newValue);
    }

    public Number getNumber() {
        return m_numberField.getNumber();
    }

    public void addChangeListener(ChangeListener changeListener) {
        if (m_listenerList == null) {
            m_listenerList = new EventListenerList();
            m_numberField.addChangeListener(m_listener);
        }
        m_listenerList.add(ChangeListener.class,changeListener);
    }

    public void removeChangeListener(ChangeListener changeListener) {
        if (m_listenerList == null) {
            return;
        }
        m_listenerList.remove(ChangeListener.class,changeListener);
    }


    class Listener implements ChangeListener,ActionListener {
        public void actionPerformed(ActionEvent evt) {
            m_numberField.requestFocus();
            if (evt.getSource() == m_upButton) {
                m_numberField.increase();
            }
            if (evt.getSource() == m_downButton) {
                m_numberField.decrease();
            }
            SwingUtilities.invokeLater(() -> {
                stateChanged(null);
                m_numberField.selectAll();
            }

            );
        }

        public void stateChanged(ChangeEvent originalEvent) {
            if (m_listenerList == null)
                return;


            ChangeEvent evt = new ChangeEvent(RaplaNumber.this);
            Object[] listeners = m_listenerList.getListenerList();
            for (int i = listeners.length-2; i>=0; i-=2) {
                if (listeners[i]==ChangeListener.class) {
                    ((ChangeListener)listeners[i+1]).stateChanged(evt);
                }
            }
        }

    }
    
    
    
}
