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
package org.rapla.gui.toolkit;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.LayoutFocusTraversalPolicy;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.components.xmlbundle.LocaleSelector;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;


public class DialogUI extends JDialog
    implements
        FrameController
        ,LocaleChangeListener
{ 
    private static final long serialVersionUID = 1L;
    
    protected RaplaButton[] buttons;
    protected JComponent content;
    private JPanel jPanelButtonFrame = new JPanel();
    private JLabel label = null;
    private boolean useDefaultOptions = false;
    private boolean bClosed = false;
    private Component parent;

    private int selectedIndex = -1;
    private FrameControllerList frameList = null;
    protected boolean packFrame = true;
    private LocaleSelector localeSelector;
    private I18nBundle i18n;

    private RaplaContext context = null;
    private ButtonListener buttonListener = new ButtonListener();
    private boolean m_modal;

    private Action abortAction = new AbstractAction() {
        private static final long serialVersionUID = 1L;
        
            public void actionPerformed(ActionEvent evt) {
                close();
            }
        };


    public static Component getOwnerWindow(Component component) {
        if (component == null)
            return getInvisibleSharedFrame();
        if (component instanceof Dialog)
            return component;
        if (component instanceof Frame)
            return component;
        Container owner = component.getParent();
        return getOwnerWindow(owner);
    }

    private static String[] getDefaultOptions() {
        return new String[] {"OK"};
    }

    public DialogUI(RaplaContext sm, Dialog parent) throws RaplaException {
        super( parent );
        service( sm );
    }

    public DialogUI(RaplaContext sm, Frame parent) throws RaplaException {
        super( parent );
        service( sm );
    }

    /** @see #getInvisibleSharedFrame */
    private static JFrame invisibleSharedFrame;

    /** @see #getInvisibleSharedFrame */
    private static int referenceCounter = 0;

    /** If a dialogs owner is null this frame will be used as owner.
        A call to this method will increase the referenceCounter.
        A new shared frame is created when the referenceCounter is 1.
        The frame gets disposed if the refernceCounter is 0.
        The referenceCounter is decreased in the dispose method.
     */
    private static Frame getInvisibleSharedFrame() {
        referenceCounter ++;
        if (referenceCounter == 1)
        {
            invisibleSharedFrame = new JFrame();
            invisibleSharedFrame.setSize(400,400);
            FrameControllerList.centerWindowOnScreen(invisibleSharedFrame);
        }
        return invisibleSharedFrame;
    }

    public static DialogUI create(RaplaContext context,Component owner,boolean modal,JComponent content,String[] options) throws RaplaException {
        DialogUI dlg;
        Component topLevel = getOwnerWindow(owner);
        if ( topLevel instanceof Dialog)
            dlg = new DialogUI(context,(Dialog)topLevel);
        else
            dlg = new DialogUI(context,(Frame)topLevel);

        dlg.parent = owner;
        dlg.init(modal,content,options);
        return dlg;
    }


    public static DialogUI create(RaplaContext context,Component owner,boolean modal,String title,String text,String[] options) throws RaplaException {
        DialogUI dlg= create(context,owner,modal,new JPanel(),options);
        dlg.createMessagePanel(text);
        dlg.setTitle(title);
        return dlg;
    }



    public static DialogUI create(RaplaContext context,Component owner,boolean modal,String title,String text) throws RaplaException {
        DialogUI dlg = create(context,owner,modal,title,text,getDefaultOptions());
        dlg.useDefaultOptions = true;
        return dlg;
    }

    public RaplaButton getButton(int index) {
        return buttons[index];
    }

    protected void init(boolean modal,JComponent content,String[] options) {
        super.setModal(modal);
        m_modal = modal;
        this.setFocusTraversalPolicy( new LayoutFocusTraversalPolicy()
        {
            private static final long serialVersionUID = 1L;

            protected boolean accept(Component component) {
                return !(component instanceof HTMLView) ;
            }
        } );

        this.content = content;

        this.enableEvents(AWTEvent.WINDOW_EVENT_MASK);

        JPanel contentPane = (JPanel) this.getContentPane();
        contentPane.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        contentPane.setLayout(new BorderLayout());
        contentPane.add(content, BorderLayout.CENTER);
        contentPane.add(jPanelButtonFrame,BorderLayout.SOUTH);
        jPanelButtonFrame.setLayout(new FlowLayout(FlowLayout.CENTER));
        setButtons(options);
        contentPane.setVisible(true);

    /*
      We enable the escape-key for executing the abortCmd. Many thanks to John Zukowski.
         <a href="http://www.javaworld.com/javaworld/javatips/jw-javatip72.html">Java-Tip 72</a>
    */
        KeyStroke stroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        contentPane.getActionMap().put("abort",buttonListener);
        contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke,"abort");
    }

    protected void setButtons(String[] options) {
        buttons = new RaplaButton[options.length];

        for (int i=0;i<options.length;i++) {
            buttons[i] = new RaplaButton(options[i],RaplaButton.DEFAULT);
            buttons[i].addActionListener(buttonListener);
            buttons[i].setAction(abortAction);
            buttons[i].setDefaultCapable(true);
        }
        jPanelButtonFrame.removeAll();
        jPanelButtonFrame.add(createButtonPanel());
        if (options.length>0)
            setDefault(0);
        jPanelButtonFrame.invalidate();
    }

    protected JComponent createButtonPanel() {
        GridLayout gridLayout = new GridLayout();
        JPanel jPanelButtons = new JPanel();
        jPanelButtons.setLayout(gridLayout);
        gridLayout.setRows(1);
        gridLayout.setHgap(10);
        gridLayout.setVgap(5);
        gridLayout.setColumns(buttons.length);
        for (int i=0;i<buttons.length;i++) {
            jPanelButtons.add(buttons[i]);
        }
        return jPanelButtons;
    }

    class ButtonListener extends AbstractAction {
        private static final long serialVersionUID = 1L;
        public void actionPerformed(ActionEvent evt) {
            for (int i=0;i<buttons.length;i++) {
                if (evt.getSource() == buttons[i]) {
                    selectedIndex = i;
                    return;
                }
            }
            selectedIndex = -1;
            abortAction.actionPerformed(new ActionEvent(DialogUI.this, ActionEvent.ACTION_PERFORMED,""));
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setAbortAction(Action action) {
        abortAction = action;
    }


    private void service(RaplaContext context) throws RaplaException {
        this.context = context;
    	i18n = context.lookup(RaplaComponent.RAPLA_RESOURCES);
    	if (useDefaultOptions) {
    		if (buttons.length > 1) {
    			getButton(0).setText(i18n.getString("ok"));
    			getButton(1).setIcon(i18n.getIcon("icon.abort"));
    			getButton(1).setText(i18n.getString("abort"));
    		} else {
    			getButton(0).setText(i18n.getString("ok"));
    		}
    	}
    	localeSelector = context.lookup( LocaleSelector.class);
    	localeSelector.addLocaleChangeListener(this);
    	frameList =  context.lookup(FrameControllerList.class);
    	frameList.add(this);
    }

    protected I18nBundle getI18n() {
        return i18n;
    }

    protected RaplaContext getContext() {
        return context;
    }

    /** the default implementation does nothing. Override this method
     if you want to react on a locale change.*/
    public void localeChanged(LocaleChangeEvent evt) {

    }

    public void setIcon(Icon icon) {
        try {
            if (label != null)
                label.setIcon(icon);
        } catch (Exception ex) {
        }
    }

    FrameControllerList getFrameList() {
        return frameList;
    }

    /** close and set the selectedIndex to the index Value. Usefull for modal dialogs*/
    public void close(int index) {
        selectedIndex = index;
        close();
    }

    // The implementation of the FrameController Interface
    public void close() {
        if (bClosed)
            return;
        dispose();
    }

    public void dispose() {
        bClosed = true;
        try {
            if (getOwner() == invisibleSharedFrame)
                referenceCounter --;
            super.dispose();
            if (referenceCounter == 0 && invisibleSharedFrame!= null)
                invisibleSharedFrame.dispose();
            if (frameList != null)
                frameList.remove(this);
            if ( localeSelector != null )
                localeSelector.removeLocaleChangeListener(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // The implementation of the DialogController Interface
    public void setDefault(int index) {
        this.getRootPane().setDefaultButton(getButton(index));
    }

    public void setTitle(String title) {
        super.setTitle(title);
    }

    public boolean isClosed() {
        return bClosed;
    }

    public void start(Point p) {
        //Validate frames that have preset sizes
        //Pack frames that have useful preferred size info, e.g. from their layout
        if (packFrame) {
            this.pack();
        } else {
            this.validate();
        }
        if (parent != null) {
            FrameControllerList.placeRelativeToComponent(this,parent,p);
        } else {
            getFrameList().placeRelativeToMain(this);
        }
        if ( initFocusComponent != null)
        {
            initFocusComponent.requestFocus();
        }
        //      okButton.requestFocus();
        bClosed = false;
        super.setVisible( true );
        if (m_modal) {
            dispose();
        }
    }
    
    Component initFocusComponent;
    public void setInitFocus(Component component)
    {
        initFocusComponent = component;
    }

    public void start() {
        start(null);
    }

    public void startNoPack() {
        packFrame = false;
        start(null);
    }

    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            abortAction.actionPerformed(new ActionEvent(this,ActionEvent.ACTION_PERFORMED,""));
        } else if (e.getID() == WindowEvent.WINDOW_CLOSED) {
            close();
        }
    }

    private void createMessagePanel(String text) {
        JPanel panel = (JPanel) content;
        panel.setLayout(new BoxLayout(panel,BoxLayout.X_AXIS));
        label = new JLabel();

        HTMLView textView = new HTMLView();
        JEditorPaneWorkaround.packText(textView, HTMLView.createHTMLPage(text)  ,450);
        JPanel jContainer = new JPanel();
        jContainer.setLayout(new BorderLayout());
        panel.add(jContainer);
        jContainer.add(label,BorderLayout.NORTH);
        panel.add(textView);
    }

}

















