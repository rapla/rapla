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
package org.rapla.client.swing.toolkit;

import org.apache.xpath.operations.Bool;
import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.internal.CommandAbortedException;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.i18n.BundleManager;
import org.rapla.components.i18n.internal.DefaultBundleManager;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.components.xmlbundle.LocaleChangeEvent;
import org.rapla.components.xmlbundle.LocaleChangeListener;
import org.rapla.entities.DependencyException;
import org.rapla.framework.RaplaInitializationException;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;
import org.rapla.scheduler.CommandScheduler;
import org.rapla.scheduler.CompletablePromise;
import org.rapla.scheduler.Promise;
import org.rapla.storage.dbrm.RaplaConnectException;
import org.rapla.storage.dbrm.RaplaRestartingException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.util.ArrayList;
import java.util.Iterator;


public class DialogUI extends JDialog
    implements
        LocaleChangeListener
        ,DialogInterface
{
    private static final long serialVersionUID = 1L;

    protected RaplaButton[] buttons;
    protected JComponent content;
    private JPanel jPanelButtonFrame = new JPanel();
    private JLabel label = null;
    private boolean useDefaultOptions = false;
    private boolean bClosed = false;
    private Component parent;
    CompletablePromise<Integer> completable;
    private int selectedIndex = -1;
    protected boolean packFrame = true;
    private DefaultBundleManager localeSelector;
    private RaplaResources i18n;
    private CommandScheduler scheduler;

    private ButtonListener buttonListener = new ButtonListener();
    private boolean m_modal;

    private Runnable abortAction = () -> close();
    private final RaplaImages images;

    protected Point p = null;


    public static Component getOwnerWindow(Component component) {
        if (component == null)
        {
            RaplaGUIComponent.getMainComponentDeprecated();
        }
//            return getInvisibleSharedFrame();
        if (component instanceof Dialog)
            return component;
        if (component instanceof Frame)
            return component;
        Container owner = component.getParent();
        return getOwnerWindow(owner);
    }

    public DialogUI(RaplaResources i18n, RaplaImages images, BundleManager bundleManager,CommandScheduler scheduler, Dialog parent) throws RaplaInitializationException {
        super( parent );
        this.scheduler = scheduler;
        this.images = images;
        service( i18n, images, bundleManager );
    }

    public DialogUI(RaplaResources i18n, RaplaImages images, BundleManager bundleManager, CommandScheduler scheduler, Frame parent) throws
            RaplaInitializationException {
        super( parent );
        this.images = images;
        this.scheduler = scheduler;
        service( i18n, images, bundleManager );
    }

    public RaplaButton getButton(int index) {
        return buttons[index];
    }

    protected void init(boolean modal,JComponent content,String[] options) {

        //super.setModal(modal);
        completable = scheduler.createCompletable();
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

    private static class UiDialogAction extends AbstractAction implements DialogAction{

        private final RaplaImages raplaImages;
        private Runnable action;

        public UiDialogAction(RaplaImages raplaImages)
        {
            this.raplaImages = raplaImages;
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            action.run();
        }

        @Override
        public void setRunnable(Runnable action)
        {
            this.action = action;

        }

        @Override
        public void setIcon(String iconKey)
        {
            putValue(LARGE_ICON_KEY, raplaImages.getIconFromKey(iconKey));
        }

        @Override
        public void execute()
        {
            action.run();
        }

    }

    protected void setButtons(String[] options) {
        buttons = new RaplaButton[options.length];

        for (int i=0;i<options.length;i++) {
            buttons[i] = new RaplaButton(options[i],RaplaButton.DEFAULT);
            buttons[i].addActionListener(buttonListener);
            final UiDialogAction action = new UiDialogAction(images);
            action.setRunnable(abortAction);
            buttons[i].setAction(action);
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
                    completable.complete( i);
                    return;
                }
            }
            selectedIndex = -1;
            //completable.completeExceptionally( new CommandAbortedException("dlg"));
            abortAction.run();//actionPerformed(new ActionEvent(DialogUI.this, ActionEvent.ACTION_PERFORMED,""));
            completable.complete( -1);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public void setAbortAction(Runnable action) {
        abortAction = action;
    }


    private void service(RaplaResources i18n, RaplaImages images, BundleManager bundleManager) throws RaplaInitializationException {
        this.i18n = i18n;
        setGlassPane(new DisabledGlassPane());
        if (useDefaultOptions) {
    		if (buttons.length > 1) {
    			getButton(0).setText(i18n.getString("ok"));
    			getButton(1).setIcon(images.getIconFromKey("icon.abort"));
    			getButton(1).setText(i18n.getString("abort"));
    		} else {
    			getButton(0).setText(i18n.getString("ok"));
    		}
    	}
    	localeSelector = (DefaultBundleManager) bundleManager;
    	localeSelector.addLocaleChangeListener(this);
    }

    protected I18nBundle getI18n() {
        return i18n;
    }

    /** the default implementation does nothing. Override this method
     if you want to react on a locale change.*/
    public void localeChanged(LocaleChangeEvent evt) {

    }

    @Override
    public void setIcon(String iconKey)
    {
        setIcon(images.getIconFromKey(iconKey));
    }

    public void setIcon(Icon icon) {
        try {
            if (label != null)
                label.setIcon(icon);
        } catch (Exception ex) {
        }
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
            super.dispose();
            if ( localeSelector != null )
                localeSelector.removeLocaleChangeListener(this);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // The implementation of the DialogController Interface
    @Override
    public void setDefault(int index) {
        this.getRootPane().setDefaultButton(getButton(index));
    }

    @Override
    public void setTitle(String title) {
        //super.setTitle(title);
    }

    public boolean isClosed() {
        return bClosed;
    }

    @Override
    public void setPosition(double x, double y)
    {
        this.p = new Point((int)x, (int)y);
    }

    public void start(Point p) {
        //Validate frames that have preset sizes
        //Pack frames that have useful preferred size info, e.g. from their layout
        if (packFrame) {
            this.pack();
        } else {
            this.validate();
        }
        if (parent != null )
            setLocationRelativeTo(parent);

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
    @Override
    public DialogAction getAction(int commandIndex)
    {
        return (DialogAction) buttons[commandIndex].getAction();
    }

    @Override
    public Promise<Integer> start(boolean pack) {
        packFrame = pack;
        start(p);
        return completable;
    }

    @Override
    public void busy(String message) {
        ((DisabledGlassPane)getGlassPane()).activate(message);
    }

    @Override
    public void idle() {
        ((DisabledGlassPane)getGlassPane()).deactivate();
    }

//    protected void processWindowEvent(WindowEvent e) {
//        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
//            abortAction.run();//actionPerformed(new ActionEvent(this,ActionEvent.ACTION_PERFORMED,""));
//            completable.complete( -1);
//        } else if (e.getID() == WindowEvent.WINDOW_CLOSED) {
//            close();
//        }
//    }

    ArrayList<VetoableChangeListener> listenerList = new ArrayList<VetoableChangeListener>();
    @Override
    protected void processWindowEvent(WindowEvent e) {
        final int id = e.getID();
        if (id == WindowEvent.WINDOW_CLOSING) {
            try {
                fireFrameClosing();
                close();
            } catch (PropertyVetoException ex) {
                return;
            }
        }
        super.processWindowEvent(e);
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        listenerList.add(listener);
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        listenerList.remove(listener);
    }

    public VetoableChangeListener[] getVetoableChangeListeners() {
        return listenerList.toArray(new VetoableChangeListener[]{});
    }



    protected void fireFrameClosing() throws PropertyVetoException {
        if (listenerList.size() == 0)
            return;
        // The propterychange event indicates that the window
        // is closing.
        PropertyChangeEvent evt = new PropertyChangeEvent(
                this
                ,"visible"
                ,new Boolean(true)
                ,new Boolean(false)
        )
                ;
        VetoableChangeListener[] listeners = getVetoableChangeListeners();
        for (int i = 0;i<listeners.length; i++) {
            listeners[i].vetoableChange(evt);
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
    
    @Singleton
    @DefaultImplementation(context=InjectionContext.swing, of=DialogUiFactoryInterface.class)
    public static class DialogUiFactory implements DialogUiFactoryInterface
    {
        private final RaplaResources i18n;
        private final RaplaImages images;
        private final BundleManager bundleManager;
        private final Logger logger;
        private final CommandScheduler scheduler;

        @Inject
        public DialogUiFactory(RaplaResources i18n, RaplaImages images, CommandScheduler scheduler,BundleManager bundleManager, Logger logger)
        {
            this.i18n = i18n;
            this.images = images;
            this.scheduler = scheduler;
            this.bundleManager = bundleManager;
            this.logger = logger;
        }

        private RaplaImages getImages()
        {
            return images;
        }
        
        

        /* (non-Javadoc)
         * @see org.rapla.client.swing.toolkit.DialogUiFactoryInterface#create(org.rapla.client.PopupContext, boolean, javax.swing.JComponent, java.lang.String[])
         */
        @Override
        public DialogInterface create(PopupContext popupContext, boolean modal, Object content, String[] options)
        {
            DialogUI dlg;
            Component parent = SwingPopupContext.extractParent(popupContext);
            Component topLevel = getOwnerWindow(parent);
            if (topLevel instanceof Dialog)
                dlg = new DialogUI(i18n, images, bundleManager, scheduler, (Dialog) topLevel);
            else
                dlg = new DialogUI(i18n, images, bundleManager, scheduler, (Frame) topLevel);
            
            dlg.parent = parent;
            dlg.init(modal, (JComponent)content, options);
            return dlg;
        }

        @Override
        public DialogInterface create(PopupContext popupContext, String title, String text, String[] options)
        {
            DialogUI dlg = (DialogUI) create(popupContext, false, new JPanel(), options);
            Point point = null;
            if ( popupContext instanceof SwingPopupContext)
            {
                SwingPopupContext casted = (SwingPopupContext)popupContext;
                point = casted.getPoint();
                if ( point != null)
                {
                    dlg.setPosition( point.getX(), point.getY());
                }
            }
            dlg.createMessagePanel(text);
            dlg.setTitle(title);
            return dlg;
        }

        @Override
        public DialogInterface create(PopupContext popupContext, String title, String text)
        {
            DialogUI dlg = (DialogUI) create(popupContext, title, text, DialogUiFactoryInterface.Util.getDefaultOptions());
            dlg.useDefaultOptions = true;
            return dlg;
        }

        @Override
        public Void showException(Throwable ex, PopupContext popupContext)
        {
            if ( popupContext == null)
            {
                popupContext = createPopupContext( null);
            }
            return showException(ex, popupContext, i18n, getImages(), logger);
        }

        private Void showException(Throwable ex, PopupContext popupContext, RaplaResources i18n, RaplaImages raplaImages, Logger logger)
        {
            if ( ex instanceof CommandAbortedException)
            {
                return null;
            }
            final Runnable runnable = () ->
            {
                Component owner = SwingPopupContext.extractParent(popupContext);
                if (ex instanceof RaplaConnectException)
                {
                    String message = ex.getMessage();
                    Throwable cause = ex.getCause();
                    String additionalInfo = "";
                    if (cause != null)
                    {
                        additionalInfo = " " + cause.getClass() + ":" + cause.getMessage();
                    }

                    logger.warn(message + additionalInfo);
                    if (ex instanceof RaplaRestartingException)
                    {
                        return;
                    }
                    try
                    {
                        ErrorDialog dialog = new ErrorDialog(logger, i18n, raplaImages, this);
                        dialog.showWarningDialog(message, owner);
                    }
                    catch (Throwable e)
                    {
                        logger.error(e.getMessage(), e);
                    }
                    return;
                }
                try
                {
                    ErrorDialog dialog = new ErrorDialog(logger, i18n, raplaImages, this);
                    if (ex instanceof DependencyException)
                    {
                        dialog.showWarningDialog(getHTML((DependencyException) ex), owner);
                    }
                    else if (Util.isWarningOnly(ex))
                    {
                        dialog.showWarningDialog(ex.getMessage(), owner);
                    }
                    else
                    {
                        dialog.showExceptionDialog(ex, owner);
                    }
                }
                catch (Throwable ex2)
                {
                    logger.error(ex2.getMessage(), ex2);
                }
            };
            try
            {
                if ( SwingUtilities.isEventDispatchThread())
                {
                    runnable.run();
                }
                else
                {
                    SwingUtilities.invokeAndWait(runnable);
                }
            }
            catch (Exception e)
            {
                logger.error(ex.getMessage(), ex);
            }
            return Promise.VOID;
        }

        static private String getHTML(DependencyException ex)
        {
            StringBuffer buf = new StringBuffer();
            buf.append(ex.getMessageText() + ":");
            buf.append("<br><br>");
            Iterator<String> it = ex.getDependencies().iterator();
            int i = 0;
            while (it.hasNext())
            {
                Object obj = it.next();
                buf.append((++i));
                buf.append(") ");

                buf.append(obj);

                buf.append("<br>");
                if (i == 30 && it.hasNext())
                {
                    buf.append("... " + (ex.getDependencies().size() - 30) + " more");
                    break;
                }
            }
            return buf.toString();
        }

        /* (non-Javadoc)
         * @see org.rapla.client.swing.toolkit.DialogUiFactoryInterface#showWarning(java.lang.String, org.rapla.client.PopupContext)
         */
        @Override
        public Void showWarning(final String warning, final PopupContext popupContext)
        {
            Runnable runnable = ()->
            {
                PopupContext popupContext2 = popupContext;
                if (popupContext2 == null)
                {
                    popupContext2 = createPopupContext(null);
                }
                showWarning(warning, popupContext2, i18n, getImages(), logger);
            };
            try
            {
                if ( SwingUtilities.isEventDispatchThread())
                {
                    runnable.run();
                }
                else
                {
                    SwingUtilities.invokeAndWait(runnable);
                }
            }
            catch (Exception e)
            {
                logger.error(e.getMessage(), e);
            }
            return null;
        }

        @Override public PopupContext createPopupContext(RaplaWidget widget)
        {
        	final Component component;
        	if ( widget == null)
        	{
        		component = getMainWindow();
        	}
        	else
        	{
        		final Object componentObj = widget.getComponent();
        		component = (componentObj instanceof Component) ? (Component) componentObj: null;
        		
        	}
            return new SwingPopupContext(component,null);
        }

        private void showWarning(String warning, PopupContext popupContext, RaplaResources i18n, RaplaImages raplaImages, Logger logger)
        {
            try
            {
                Component owner = SwingPopupContext.extractParent(popupContext);
                ErrorDialog dialog = new ErrorDialog(logger, i18n, raplaImages, this);
                dialog.showWarningDialog(warning, owner);
            }
            catch (Throwable ex2)
            {
                logger.error(ex2.getMessage(), ex2);
            }
        }

        @Override
        public void busy(String text)
        {

            ((RaplaFrame)getMainWindow()).busy( text);
        }

        @Override
        public void idle()
        {
            ((RaplaFrame)getMainWindow()).idle();
        }

        public Component getMainWindow() {
            return RaplaGUIComponent.getMainComponentDeprecated();
        }
    }

}

















