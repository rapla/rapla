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

import java.awt.ComponentOrientation;

/**
 * The NumberField only accepts integer values.
 * <strong>Warning!</strong> Currently only Longs are supported.
 */

public class NumberField extends AbstractBlockField {
    private static final long serialVersionUID = 1L;

    static char[] m_separators = new char[0];
    Number m_number;
    Number m_minimum;
    Number m_maximum;
    /**
     * @return Returns the m_blockStepSize.
     */
    public int getBlockStepSize() {
        return m_blockStepSize;
    }
    /**
     * @param stepSize The m_blockStepSize to set.
     */
    public void setBlockStepSize(int stepSize) {
        m_blockStepSize = stepSize;
    }
    /**
     * @return Returns the m_stepSize.
     */
    public int getStepSize() {
        return m_stepSize;
    }
    /**
     * @param size The m_stepSize to set.
     */
    public void setStepSize(int size) {
        m_stepSize = size;
    }
    int m_stepSize;
    int m_blockStepSize;
    int m_maxLength;
    boolean m_isNullPermitted = true;
    public NumberField() {
        setComponentOrientation( ComponentOrientation.RIGHT_TO_LEFT);
        updateColumns();
    }
    
    public NumberField(Number minimum,Number maximum,int stepSize,int blockStepSize) {
        this();
        setStepSize(stepSize);
        setBlockStepSize(blockStepSize);
        setMinimum( minimum );
        setMaximum( maximum );
    }
    
    
    public void setMinimum(Number minimum){
        m_minimum = minimum;
        updateColumns();
    }
    
    public void setMaximum(Number maximum){
        m_maximum = maximum;
        updateColumns();
    }
   
    public Number getMinimum() {
        return m_minimum;
    }

    public Number getMaximum() {
        return m_maximum;
    }

    private void updateColumns() {
        if (m_maximum!= null && m_minimum != null) {
            if ((Math.abs(m_maximum.longValue()))
                > (Math.abs(m_minimum.longValue())) * 10)
                m_maxLength = m_maximum.toString().length();
            else
                m_maxLength = m_minimum.toString().length();
            setColumns(m_maxLength);
        } else {
            m_maxLength = 100;
            setColumns(4);
        }    
    }
    
    public boolean isNullPermitted() {
        return m_isNullPermitted;
    }
    public void setNullPermitted(boolean isNullPermitted) {
        m_isNullPermitted = isNullPermitted;
        if (m_number == null && !isNullPermitted)
            m_number = new Double(defaultValue());
    }

    private long defaultValue() {
        if (m_minimum != null && m_minimum.longValue()>0)
            return m_minimum.longValue();
        else if (m_maximum != null && m_maximum.longValue()<0)
            return m_maximum.longValue();
        return 0;
    }

    public void setNumber(Number number) {
        updateNumber( number );
        m_oldText = getText();
        fireValueChanged();
    }

    private void updateNumber(Number number) {
        m_number = number;
        String text;
        if (number != null) {
            text = String.valueOf(number.longValue());
        } else {
            text = "";
        }
        String previous = getText();
		if ( previous != null && text.equals( previous))
		{
			return;
		}
        setText( text );
    }

    public void increase() {
        changeSelectedBlock(new int[1],0,"",1);
    }

    public void decrease() {
        changeSelectedBlock(new int[1],0,"",-1);
    }

    public Number getNumber() {
        return m_number;
    }

    public boolean allowsNegative() {
        return (m_minimum == null || m_minimum.longValue()<0);
    }

    protected char[] getSeparators() {
        return m_separators;
    }

    protected boolean isSeparator(char c) {
        return false;
    }

    protected void changeSelectedBlock(int[] blocks,int block,String selected,int count) {
        long longValue = ((m_number != null) ? m_number.longValue() : defaultValue());
        if (count == 1)
            longValue = longValue + m_stepSize;
        if (count == -1)
            longValue = longValue - m_stepSize;
        if (count == 10)
            longValue = longValue + m_blockStepSize;
        if (count == -10)
            longValue = longValue - m_blockStepSize;

        if (m_minimum != null && longValue<m_minimum.longValue())
            longValue = m_minimum.longValue();
        if (m_maximum != null && longValue>m_maximum.longValue())
            longValue = m_maximum.longValue();
        updateNumber(new Long(longValue));
        calcBlocks(blocks);
        markBlock(blocks,block);
    }

    public boolean blocksValid() {
        try {
            String text = getText();
            if (text.length() ==0) {
                if (isNullPermitted())
                    m_number = null;
                return true;
            }

            long newLong = Long.parseLong(text);
            if ((m_minimum == null || newLong>=m_minimum.longValue())
                && (m_maximum == null || newLong<=m_maximum.longValue())) {
                m_number = new Long(newLong);
                return true;
            }
        } catch (NumberFormatException e) {
            if (isNullPermitted())
                m_number = null;
        }
        return false;
    }

    protected int blockCount() {
        return 1;
    }

    protected int maxBlockLength(int block) {
        return m_maxLength;
    }

    protected boolean isValidChar(char c) {
        return (Character.isDigit(c) || (allowsNegative() && c=='-'));
    }

}
