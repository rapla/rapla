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
package org.rapla.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.calendar.RaplaTime;
import org.rapla.components.calendar.TimeRenderer;
import org.rapla.components.i18n.I18nBundle;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.IOUtil;
import org.rapla.entities.User;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URI;
import java.security.AccessControlException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
    Base class for most components in the gui package. Eases
    access to frequently used services, e.g. {@link I18nBundle}.
    It also provides some methods for Exception displaying.
 */
public class RaplaGUIComponent extends RaplaComponent
{
    private static Component mainComponent;
    ClientFacade clientFacade;

    @Deprecated
    public static void setMainComponent( final Component mainComponent )
    {
        RaplaGUIComponent.mainComponent = mainComponent;
    }

    public RaplaGUIComponent( final ClientFacade facade, final RaplaResources i18n, final RaplaLocale raplaLocale, final Logger logger )
    {
        super(facade.getRaplaFacade(), i18n, raplaLocale, logger);
        this.clientFacade = facade;
    }

    protected User getUser() throws RaplaException
    {
        return clientFacade.getUser();
    }

    public CalendarOptions getCalendarOptions()
    {
        User user;
        try
        {
            user = getUser();
        }
        catch ( final RaplaException ex )
        {
            // Use system settings if an error occurs
            user = null;
        }
        return getCalendarOptions(user);
    }

    /** returns if the session user is admin */
    final public boolean isAdmin()
    {
        try
        {
            return getUser().isAdmin();
        }
        catch ( final RaplaException ex )
        {
        }
        return false;
    }

    final public boolean isModifyPreferencesAllowed()
    {
        try
        {
            final User user = getUser();
            return isModifyPreferencesAllowed(user);
        }
        catch ( final RaplaException ex )
        {
        }
        return false;
    }

    protected Date getStartDate( final CalendarModel model ) throws RaplaException
    {
        final RaplaFacade raplaFacade = getFacade();
        return getStartDate(model, raplaFacade, getUser());
    }

    public ClientFacade getClientFacade()
    {
        return clientFacade;
    }

    protected PopupContext createPopupContext( final Component parent, final Point p )
    {
        return new SwingPopupContext(parent, p);
    }

    static public RaplaCalendar createRaplaCalendar( final DateRenderer dateRenderer, final IOInterface service, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger )
    {
        final RaplaCalendar cal = new RaplaCalendar(i18n.getLocale(), IOUtil.getTimeZone());
        cal.setDateRenderer(dateRenderer);
        addCopyPaste(cal.getDateField(), i18n, raplaLocale, service, logger);
        return cal;
    }

    static Color NON_WORKTIME = new Color(0xcc, 0xcc, 0xcc);

    final protected TimeRenderer getTimeRenderer()
    {
// BJO 00000070
        final int start = getCalendarOptions().getWorktimeStartMinutes();
        final int end = getCalendarOptions().getWorktimeEndMinutes();
// BJO 00000070
        return new TimeRenderer()
        {
            @Override
            public Color getBackgroundColor( final int hourOfDay, final int minute )
            {
// BJO 00000070
                final int worktime = hourOfDay * 60 + minute;
// BJO 00000070 
                if ( start >= end )
                {
// BJO 00000070 
                    if ( worktime >= end && worktime < start )
// BJO 00000070 
                    {
                        return NON_WORKTIME;
                    }
                }
// BJO 00000070 
                else if ( worktime < start || worktime >= end )
                {
// BJO 00000070 
                    return NON_WORKTIME;
                }
                return null;
            }

            @Override
            public String getToolTipText( final int hourOfDay, final int minute )
            {
                return null;
            }

            @Override
            public String getDurationString( final int durationInMinutes )
            {
                if ( durationInMinutes > 0 )
                {
                    final int hours = durationInMinutes / 60;
                    final int minutes = durationInMinutes % 60;
                    if ( hours == 0 )
                    {
                        return "(" + minutes + " " + getString("minutes.abbreviation") + ")";
                    }

                    if ( minutes % 30 != 0 )
                    {
                        return "";
                    }
                    final StringBuilder builder = new StringBuilder();
                    builder.append(" (");

                    if ( hours > 0 )
                    {
                        builder.append(hours);
                    }
                    if ( minutes % 60 != 0 )
                    {
                        final char c = 189; // 1/2
                        builder.append(c);
                    }
                    if ( minutes % 30 == 0 )
                    {
                        builder.append(" " + getString((hours == 1 && minutes % 60 == 0 ? "hour.abbreviation" : "hours.abbreviation")) + ")");
                    }
                    return builder.toString();
                }
                return "";
            }

        };
    }

    public RaplaTime createRaplaTime( final IOInterface service )
    {
        final RaplaTime cal = new RaplaTime(getI18n().getLocale(), IOUtil.getTimeZone());
        cal.setTimeRenderer(getTimeRenderer());
        final int rowsPerHour = getCalendarOptions().getRowsPerHour();
        cal.setRowsPerHour(rowsPerHour);
        addCopyPaste(cal.getTimeField(), getI18n(), getRaplaLocale(), service, getLogger());
        return cal;
    }

    public Component getMainComponent()
    {
        return mainComponent;
    }

    static public Component getMainComponentDeprecated()
    {
        return mainComponent;
    }

    public static void addCopyPaste( final JComponent component, final RaplaResources i18n, final RaplaLocale raplaLocale, final IOInterface service, final Logger logger )
    {
        final ActionListener pasteListener = e -> paste(component, e, service, logger);
        final ActionListener copyListener = e -> copy(component, e, service, raplaLocale);
        final JPopupMenu menu = new JPopupMenu();
        {
            final JMenuItem copyItem = new JMenuItem();

            copyItem.addActionListener(copyListener);
            copyItem.setText(i18n.getString("copy"));

            menu.add(copyItem);
        }
        {
            final JMenuItem pasteItem = new JMenuItem();
            pasteItem.addActionListener(pasteListener);
            pasteItem.setText(i18n.getString("paste"));
            menu.add(pasteItem);
        }

        component.add(menu);
        component.addMouseListener(new MouseAdapter()
        {
            private void showMenuIfPopupTrigger( final MouseEvent e )
            {
                if ( e.isPopupTrigger() )
                {
                    menu.show(component, e.getX() + 3, e.getY() + 3);
                }
            }

            @Override
            public void mousePressed( final MouseEvent e )
            {
                showMenuIfPopupTrigger(e);
            }

            @Override
            public void mouseReleased( final MouseEvent e )
            {
                showMenuIfPopupTrigger(e);
            }
        });

        component.registerKeyboardAction(copyListener, i18n.getString("copy"), COPY_STROKE, JComponent.WHEN_FOCUSED);
        component.registerKeyboardAction(pasteListener, i18n.getString("paste"), PASTE_STROKE, JComponent.WHEN_FOCUSED);
    }

    public static KeyStroke COPY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.CTRL_MASK, false);
    public static KeyStroke CUT_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.CTRL_MASK, false);
    public static KeyStroke PASTE_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_V, ActionEvent.CTRL_MASK, false);

    public static void copy( final JComponent component, final ActionEvent e, final IOInterface service, final RaplaLocale raplaLocale )
    {
        final Transferable transferable;
        if ( component instanceof JTextComponent )
        {
            final String selectedText = ((JTextComponent) component).getSelectedText();
            transferable = new StringSelection(selectedText);
        }
        else if ( component instanceof JTable )
        {
            final JTable table = (JTable) component;
            transferable = getSelectedContent(table, raplaLocale);
        }
        else
        {
            transferable = new StringSelection(component.toString());
        }

        if ( transferable != null )
        {
            try
            {
                if ( service != null )
                {
                    service.setContents(transferable, null);
                }
                else
                {
                    final Action action = component.getActionMap().get(DefaultEditorKit.copyAction);
                    if ( action != null )
                    {
                        action.actionPerformed(e);
                    }
                }
            }
            catch ( final AccessControlException ex )
            {
                clipboard.set(transferable);
            }

        }
    }

    static ThreadLocal<Transferable> clipboard = new ThreadLocal<>();

    /** Code from
    http://www.javaworld.com/javatips/jw-javatip77.html
    */
    private static final String LINE_BREAK = "\n";
    private static final String CELL_BREAK = "\t";

    private static StringSelection getSelectedContent( final JTable table, final RaplaLocale raplaLocale )
    {
        final int numCols = table.getSelectedColumnCount();
        final int[] rowsSelected = table.getSelectedRows();
        final int[] colsSelected = table.getSelectedColumns();
//         int numRows=table.getSelectedRowCount(); 
//         if (numRows!=rowsSelected[rowsSelected.length-1]-rowsSelected[0]+1 || numRows!=rowsSelected.length || 
//                         numCols!=colsSelected[colsSelected.length-1]-colsSelected[0]+1 || numCols!=colsSelected.length) {
//        	 
//        	 JOptionPane.showMessageDialog(null, "Invalid Copy Selection", "Invalid Copy Selection", JOptionPane.ERROR_MESSAGE);
//        	 return null; 
//         } 

        final StringBuffer excelStr = new StringBuffer();
        for ( final int row : rowsSelected )
        {
            int j = 0;
            for ( final int col : colsSelected )
            {
                final Object value = table.getValueAt(row, col);
                String formated;
                final Class<?> columnClass = table.getColumnClass(col);
                final boolean isDate = columnClass.isAssignableFrom(java.util.Date.class);
                if ( isDate )
                {
                    final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    format.setTimeZone(IOUtil.getTimeZone());
                    if ( value instanceof java.util.Date )
                    {
                        final String timestamp = format.format((java.util.Date) value);
                        formated = timestamp;
                    }
                    else
                    {
                        final String escaped = escape(value);
                        formated = escaped;
                    }
                }
                else
                {
                    final String escaped = escape(value);
                    formated = escaped;
                }
                excelStr.append(formated);
                final boolean isLast = j == numCols - 1;
                if ( !isLast )
                {
                    excelStr.append(CELL_BREAK);
                }
                j++;
            }
            excelStr.append(LINE_BREAK);
        }

        final String string = excelStr.toString();
        final StringSelection sel = new StringSelection(string);
        return sel;
    }

    private static String escape( final Object cell )
    {
        return cell.toString().replace(LINE_BREAK, " ").replace(CELL_BREAK, " ");
    }

    /** Code End	 */

    protected static void paste( final JComponent component, final ActionEvent e, final IOInterface service, final Logger logger )
    {
        try
        {
            if ( service != null )
            {
                final Transferable transferable = service.getContents(null);
                Object transferData;
                try
                {
                    if ( transferable.isDataFlavorSupported(DataFlavor.stringFlavor) )
                    {
                        transferData = transferable.getTransferData(DataFlavor.stringFlavor);
                        if ( transferData != null )
                        {
                            if ( component instanceof JTextComponent )
                            {
                                ((JTextComponent) component).replaceSelection(transferData.toString());
                            }
                            if ( component instanceof JTable )
                            {
                                // Paste currently not supported
                            }
                        }
                    }
                    else if ( transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) )
                    {
                        transferData = transferable.getTransferData(DataFlavor.javaFileListFlavor);
                        if ( transferData != null )
                        {
                            if ( component instanceof JTextComponent )
                            {
                                final List<File> fileList = (List<File>) transferData;
                                if ( fileList.size() > 0 )
                                {
                                    final File file = fileList.get(0);
                                    final URI uri = file.getAbsoluteFile().toURI();
                                    ((JTextComponent) component).replaceSelection(uri.toString());
                                }
                            }
                            if ( component instanceof JTable )
                            {
                                // Paste currently not supported
                            }
                        }
                    }

                }
                catch ( final Exception ex )
                {
                    logger.warn(ex.getMessage(), ex);
                }

            }
            else
            {
                final Action action = component.getActionMap().get(DefaultEditorKit.pasteAction);
                if ( action != null )
                {
                    action.actionPerformed(e);
                }
            }
        }
        catch ( final AccessControlException ex )
        {
            final Transferable transferable = clipboard.get();
            if ( transferable != null )
            {
                if ( component instanceof JTextComponent )
                {
                    Object transferData;
                    try
                    {
                        transferData = transferable.getTransferData(DataFlavor.stringFlavor);
                        ((JTextComponent) component).replaceSelection(transferData.toString());
                    }
                    catch ( final Exception e1 )
                    {
                        if ( logger != null )
                        {
                            logger.error(e1.getMessage(), e1);
                        }
                    }
                }
            }
        }

    }
}
