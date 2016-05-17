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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.reflect.Method;

import javax.inject.Inject;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.rapla.RaplaResources;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.framework.RaplaException;
import org.rapla.logger.Logger;

final public class ErrorDialog {
    RaplaImages images;
    private final DialogUiFactoryInterface dialogUiFactory;
    private final RaplaResources i18n;
    private final Logger logger;


    @Inject
    public ErrorDialog(Logger logger, RaplaResources i18n, RaplaImages raplaImages, DialogUiFactoryInterface dialogUiFactory)  {
        this.logger = logger;
        this.i18n = i18n;
        this.dialogUiFactory = dialogUiFactory;
        images = raplaImages;
    }
    
    protected I18nBundle getI18n()
    {
        return i18n;
    }
    
    protected Logger getLogger()
    {
        return logger;
    }

    public static final int WARNING_MESSAGE = 1;
    public static final int ERROR_MESSAGE = 2;
    public static final int EXCEPTION_MESSAGE = 3;

    /** This is for the test-cases only. If this flag is set
        the ErrorDialog throws an ErrorDialogException instead of
        displaying the dialog. This is useful for testing. */
    public static boolean THROW_ERROR_DIALOG_EXCEPTION = false;

    private void test(String message,int type) {
        if (THROW_ERROR_DIALOG_EXCEPTION) {
            throw new ErrorDialogException(new RaplaException(message),type);
        }
    }

    private void test(Throwable ex,int type) {
        if (THROW_ERROR_DIALOG_EXCEPTION) {
            throw new ErrorDialogException(ex,type);
        }
    }

    private String createTitle(String key) {
        return getI18n().format("exclamation.format",getI18n().getString(key));
    }

    public void show(String message) {
        test(message,ERROR_MESSAGE);
        try {
            showDialog(createTitle("error"),message,null);
        } catch (Exception ex) {
            getLogger().error(message);
        }
    }

    public void showWarningDialog(String message,Component owner) {
        test(message,WARNING_MESSAGE);
        try {
            showWarningDialog(createTitle("warning"),message,owner);
        } catch (Exception ex) {
            getLogger().error(message);
        }
    }

    static private String getCause(Throwable e) {
        String message = e.getMessage();
        if (message != null && message.length() > 0) {
            return message;
        }
        Throwable cause = e.getCause();
        if (cause != null)
            message = getCause( cause );
        return message;
    }

    static public String getMessage(Throwable e) {
        String message = getCause(e);
        if (message == null || message.length() == 0)
            message = e.toString();
        return message;
    }

    public void showExceptionDialog(Throwable e,Component owner) {
        test(e,EXCEPTION_MESSAGE);
        try {
            String message = getMessage(e);
            if ( getLogger() != null )
                getLogger().error(message, e);
            JPanel component = new JPanel();
            component.setLayout( new BorderLayout());
            
            HTMLView textView = new HTMLView();
            JEditorPaneWorkaround.packText(textView, HTMLView.createHTMLPage(message)  ,450);
            component.add( textView,BorderLayout.NORTH);
            boolean showStacktrace = true;
            Throwable nestedException = e;

            do
            {
                if (  nestedException instanceof RaplaException)
                {
                    showStacktrace = false;
                    nestedException = nestedException.getCause();
                }
                else
                {
                    showStacktrace = true;
                }
            }
            while ( nestedException != null && !showStacktrace);

            if ( showStacktrace)
            {
                try {
                    Method getStackTrace =Exception.class.getMethod("getStackTrace");
                    final Object[] stackTrace = (Object[])getStackTrace.invoke( e);
                    final JList lister = new JList( );
                    final JScrollPane list = new JScrollPane(lister, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    list.setBorder( null);
                    JPanel stackTracePanel = new JPanel();
                    final JCheckBox stackTraceChooser = new JCheckBox("show stacktrace");
                    stackTracePanel.setLayout( new BorderLayout());
                    stackTracePanel.add( stackTraceChooser, BorderLayout.NORTH);
                    stackTracePanel.add( list, BorderLayout.CENTER);
                    stackTracePanel.setPreferredSize( new Dimension(300,200));
                    stackTracePanel.setMinimumSize( new Dimension(300,200));
                    component.add( stackTracePanel,BorderLayout.CENTER);
                    lister.setVisible( false );
                    stackTraceChooser.addActionListener( new ActionListener() {

                        @SuppressWarnings("unchecked")
						public void actionPerformed(ActionEvent e) {
                            DefaultListModel model =new DefaultListModel();
                            if (stackTraceChooser.isSelected() ) {
                                for ( int i=0;i< stackTrace.length;i++) {
                                    model.addElement( stackTrace[i]);
                                }
                            }
                            lister.setModel( model );
                            lister.setVisible( stackTraceChooser.isSelected());  
                        }
                        
                    });
                } catch (Exception ex) {
                }
            }

            DialogInterface dlg = dialogUiFactory.create(new SwingPopupContext(owner, null),true,component, new String[] {getI18n().getString("ok")});
            dlg.setTitle(createTitle("error"));
            dlg.setIcon("icon.error");
            dlg.start(true);
        } catch (Exception ex) {
            getLogger().error( e.getMessage(), e);
            getLogger().error("Can't show errorDialog " + ex);
        }
    }

    private void showDialog(String title, String message,Component owner) {
        try {
            DialogInterface dlg = dialogUiFactory.create(new SwingPopupContext(owner, null),true,title,message);
            dlg.setIcon("icon.error");
            dlg.start(true);
        } catch (Exception ex2) {
            getLogger().error(ex2.getMessage());
        }
    }

    public void showWarningDialog(String title, String message,Component owner) {
        try {
            DialogInterface dlg = dialogUiFactory.create(new SwingPopupContext(owner, null),true,title,message);
            dlg.setIcon("icon.warning");
            dlg.start(true);
        } catch (Exception ex2) {
            getLogger().error(ex2.getMessage());
        }
    }
}










