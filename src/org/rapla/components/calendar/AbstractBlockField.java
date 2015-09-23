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

import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.util.ArrayList;

import javax.swing.InputVerifier;
import javax.swing.JComponent;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

/** The base class for TextFields that supports entering values in
    blocks. Most notifiably the DateField and the TimeField. <br> You
    can use the left/right arrow keys to switch the blocks. Use of the
    top/down arrow keys will increase/decrease the values in the
    selected block (page_up/page_down for major changes). You can
    specify maximum length for each block. If a block reaches this
    maximum value the focus will switch to the next block.
    @see DateField
    @see TimeField
 */
public abstract class AbstractBlockField extends JTextField {
    private static final long serialVersionUID = 1L;
    int m_markedBlock = 0;
    char m_lastChar = 0;
    boolean m_keyPressed = false;
    protected String m_oldText;

    ArrayList<ChangeListener> m_listenerList = new ArrayList<ChangeListener>();

    public AbstractBlockField() {
        Listener listener = new Listener();
        addMouseListener(listener);
        addActionListener(listener);
        addFocusListener(listener);
        addKeyListener(listener);
        addMouseWheelListener( listener );
        setInputVerifier(new InputVerifier() {
            public boolean verify(JComponent comp) {
         		boolean valid = blocksValid();
         		if ( valid )
         		{
         			fireValueChanged();
         		}
            	return valid;
            }
        });
    }

    class Listener implements MouseListener,KeyListener,FocusListener,ActionListener, MouseWheelListener {
        public void mouseReleased(MouseEvent evt) {
        }
        public void mousePressed(MouseEvent evt) {
        	if ( !isMouseOverComponent())
        	{
        		blocksValid();
            	fireValueChanged();
            }
        	if ( evt.getButton() != MouseEvent.BUTTON1)
        	{
        		return;
        	}
            // We have to mark the block on mouse pressed and mouse clicked.
            // Windows needs mouse clicked while Linux needs mouse pressed.
            markCurrentBlock();
        }
        public void mouseEntered(MouseEvent me) {
        }
        public void mouseExited(MouseEvent me) {
        	blocksValid();
        	fireValueChanged();
        }
        public void mouseClicked(MouseEvent evt) {
        	if ( evt.getButton() != MouseEvent.BUTTON1)
        	{
        		return;
        	}
            // We have to mark the block on mouse pressed and mouse clicked.
            markCurrentBlock();
            if (evt.getClickCount()>1) {
                selectAll();
                return;
            }
            if (blocksValid()) {
                fireValueChanged();
            }
        }

        // Implementation of ActionListener
        public void actionPerformed(ActionEvent e) {
            blocksValid();
            fireValueChanged();
        }

        public void focusGained(FocusEvent evt) {
            if (blocksValid()) {
                //setBlock(0);
            }
        }
        public void focusLost(FocusEvent evt) {
            //select(-1,-1);
            blocksValid();
            fireValueChanged();
        }
        
        public void keyPressed(KeyEvent evt) {
            m_keyPressed = true;
            m_lastChar=evt.getKeyChar();
            if (!blocksValid())
                return;
            if (isSeparator(evt.getKeyChar())) {
                evt.consume();
                return;
            }
            switch (evt.getKeyCode()) {
            case KeyEvent.VK_LEFT:
            case KeyEvent.VK_KP_LEFT:
                if (blockCount() >1) {
                    setBlock(-1);
                    evt.consume();
                }
                return;
            case KeyEvent.VK_KP_RIGHT:
            case KeyEvent.VK_RIGHT:
                if (blockCount() >1) {
                    setBlock(1);
                    evt.consume();
                }
                return;
            case KeyEvent.VK_HOME:
                setBlock(-1000);
                evt.consume();
                return;
            case KeyEvent.VK_END:
                setBlock(1000);
                evt.consume();
                return;
            case KeyEvent.VK_KP_UP:
            case KeyEvent.VK_UP:
                changeSelectedBlock(1);
                evt.consume();
                return;
            case KeyEvent.VK_KP_DOWN:
            case KeyEvent.VK_DOWN:
                changeSelectedBlock(-1);
                evt.consume();
                        return;
            case KeyEvent.VK_PAGE_UP:
                changeSelectedBlock(10);
                evt.consume();
                return;
            case KeyEvent.VK_PAGE_DOWN:
                changeSelectedBlock(-10);
                evt.consume();
                return;
            }
            
        }

        public void keyReleased(KeyEvent evt) {
            m_lastChar=evt.getKeyChar();
            // Only change the block if the keyReleased
            // event follows a keyPressed.
            // If you type very quickly you could
            // get two strunged keyReleased events
            if (m_keyPressed == true)
                m_keyPressed = false;
            else
                        return;
            if (!blocksValid())
                return;
            if (isSeparator(m_lastChar) ) {
                if ( isSeparator( getText().charAt(getCaretPosition())))
                    nextBlock();
                evt.consume();
            } else if (isValidChar(m_lastChar)) {
                advance();
                evt.consume();
                if (blocksValid() && !isMouseOverComponent())
                {
//                	fireValueChanged();
                }
            }
        }
        
        private boolean isMouseOverComponent()
        {
           Component comp = AbstractBlockField.this;
           Point compLoc = comp.getLocationOnScreen();
           Point mouseLoc = MouseInfo.getPointerInfo().getLocation();
           boolean result = (mouseLoc.x >= compLoc.x  && mouseLoc.x <= compLoc.x + comp.getWidth()
        		   && mouseLoc.y >= compLoc.y && mouseLoc.y <= compLoc.y + comp.getHeight());
          return result;
        }
        
        public void keyTyped(KeyEvent evt) {
        }

        
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (!hasFocus())
            {
                return;
            }
            if (!blocksValid() || e == null)
                return;
            int count = e.getWheelRotation();
            changeSelectedBlock(-1 * count/(Math.abs(count)));
            
        }
    }

    private void markCurrentBlock() {
        if (!blocksValid()) {
            return;
        }
        int blockstart[] = new int[blockCount()];
        int block = calcBlocks(blockstart);
        markBlock(blockstart,block);
    }

    public void addChangeListener(ChangeListener listener) {
        m_listenerList.add(listener);
    }

    /** removes a listener from this component.*/
    public void removeChangeListener(ChangeListener listener) {
        m_listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners() {
        return m_listenerList.toArray(new ChangeListener[]{});
    }

    /** A ChangeEvent will be fired to every registered ActionListener
     *  when the value has changed.
    */
    protected void fireValueChanged() {
        if (m_listenerList.size() == 0)
            return;

        // Only fire, when text has changed.
        if (m_oldText != null && m_oldText.equals(getText()))
            return;
        m_oldText = getText();

        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0; i<listeners.length; i ++) {
            listeners[i].stateChanged(evt);
        }
    }
    

    //  switch to next block
    private void nextBlock() {
        int[] blockstart = new int[blockCount()];
        int block = calcBlocks(blockstart);
        markBlock(blockstart,Math.min(block + 1,blockCount() -1));
    }

    //  switch to next block if blockend is reached and
    private void advance() {
        if (blockCount()<2)
            return;
        int blockstart[] = new int[blockCount()];
        int block = calcBlocks(blockstart);
        if (block >= blockCount() -1)
            return;
        int selectedLen = (block ==0) ? blockstart[1] : (blockstart[block + 1] - blockstart[block] - 1);
        if (selectedLen == maxBlockLength(block)) {
            markBlock(blockstart,Math.min(block + 1,blockCount() -1));
        }
    }

    /**  changes the value of the selected Block. Adds the count value. */
    private void changeSelectedBlock(int count) {
        int blockstart[] = new int[blockCount()];
        int block = calcBlocks(blockstart);
        int start = (block == 0) ? 0 : blockstart[block] + 1;
        int end = (block == blockCount() -1) ? getText().length() : blockstart[block+1];
        String selected = getText().substring(start,end);
        markBlock(blockstart,block);
        changeSelectedBlock(blockstart,block,selected,count);
    }

    private void setBlock(int count) {
        if (blockCount()<1)
            return;
        int blockstart[] = new int[blockCount()];
        int block = calcBlocks(blockstart);
        if (count !=0)
            markBlock(blockstart,Math.min(Math.max(0,block + count),blockCount()-1));
        else
            markBlock(blockstart,m_markedBlock);
    }


    /** Select the specified block. */
    final protected void markBlock(int[] blockstart,int block) {
        m_markedBlock = block;
        if (m_markedBlock == 0)
            mark(blockstart[0], (blockCount()>1) ? blockstart[1] : getText().length());
        else if (m_markedBlock==blockCount()-1)
            mark(blockstart[m_markedBlock] + 1,getText().length());
        else
            mark(blockstart[m_markedBlock] + 1,blockstart[m_markedBlock+1]);
    }

    private boolean isPrevSeparator(int i,char[] source,String currentText,int offs) {
        return (i>0 && isSeparator(source[i-1])
            || (offs > 0 && isSeparator(currentText.charAt(offs-1))));
    }
    private boolean isNextSeparator(String currentText,int offs) {
        return (offs < currentText.length()-1 && isSeparator(currentText.charAt(offs)));
    }

    class DateDocument extends PlainDocument {
        private static final long serialVersionUID = 1L;

        public void insertString(int offs, String str, AttributeSet a)
                        throws BadLocationException {
            String currentText = getText(0, getLength());
            char[] source = str.toCharArray();
            char[] result = new char[source.length];
            int j = 0;
            boolean bShouldBeep = false;
            for (int i = 0; i < source.length; i++) {
                boolean bValid = true;
                if (!isValidChar(source[i])) {
                    bValid = false;
                } else {
                    //if (offs < currentText.length()-1)
                    if (isSeparator(source[i]))
                        if (isNextSeparator(currentText,offs)
                            || isPrevSeparator(i,source,currentText,offs)
                            || (i==0 && offs==0)
                            || (i==source.length && offs==currentText.length())
                            )
                        {
                            //                      beep();
                            continue;
                        }
                }
                if (bValid)
                    result[j++] = source[i];
                else
                    bShouldBeep = true;
            }
            if (bShouldBeep)
                beep();
            final String insertedString = new String(result, 0, j);
            super.insertString(offs, insertedString, a);
        }
    }

    final protected Document createDefaultModel() {
        return new DateDocument();
    }

    /** Calculate the blocks. The new blockstarts will be stored in
        the int array.
        @return the block that contains the caret.
    */
    protected int calcBlocks(int[] blockstart) {
        String text = getText();
        int dot = getCaretPosition();
        int block = 0;
        blockstart[0] = 0;
        char[] separators = getSeparators();
        for (int i=1;i<blockstart.length;i++) {
            int min = text.length();
            for (int j=0;j<separators.length;j++) {
                int pos = text.indexOf(separators[j], blockstart[i-1] + 1);
                if (pos>=0 && pos < min)
                    min = pos;
            }
            blockstart[i] = min;
            if (dot>blockstart[i])
                block = i;
        }
        return block;
    }

    /** Select the text from dot to mark. */
    protected void mark(int dot,int mark) {
        setCaretPosition(mark);
        moveCaretPosition(dot);
    }

    /** this method will be called when an non-valid character is entered. */
    protected void beep() {
        Toolkit.getDefaultToolkit().beep();
    }

    /** returns true if the text can be split into blocks. */
    abstract public boolean blocksValid();
    /** The number of blocks for this Component.  */
    abstract protected int blockCount();
    /** This method will be called, when the user has pressed the up/down
     * arrows on a selected block.
     * @param count Posible values are 1,-1,10,-10.
    */
    abstract protected void changeSelectedBlock(int[] blocks,int block,String selected,int count);
    /** returns the maximum length of the specified block.  */
    abstract protected int maxBlockLength(int block);
    /** returns true if the character should be accepted by the component.   */
    abstract protected boolean isValidChar(char c);
    /** returns true if the character is a block-separator. All block-separators must
    be valid characters.
    @see #isValidChar
    */
    abstract protected boolean isSeparator(char c);
    /** @return all seperators.*/
    abstract protected char[] getSeparators();

}
