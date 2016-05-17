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

import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.calendar.RaplaTime;
import org.rapla.components.calendar.TimeRenderer;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.User;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;

/**
    Base class for most components in the gui package. Eases
    access to frequently used services, e.g. {@link org.rapla.components.xmlbundle.I18nBundle}.
    It also provides some methods for Exception displaying.
 */
public class RaplaGUIComponent extends RaplaComponent
{
    private static Component mainComponent;
	ClientFacade clientFacade;

	@Deprecated
	public static void setMainComponent(Component mainComponent)
	{
		RaplaGUIComponent.mainComponent = mainComponent;
	}

	public RaplaGUIComponent(ClientFacade facade,RaplaResources i18n, RaplaLocale raplaLocale, Logger logger)
    {
        super(facade.getRaplaFacade(), i18n, raplaLocale, logger);
		this.clientFacade = facade;
    }

	protected User getUser() throws RaplaException
	{
		return clientFacade.getUser();
	}

	/** lookupDeprecated UpdateModule from the serviceManager */
	protected ClientFacade getUpdateModule() {
		return clientFacade;
	}

	/** lookupDeprecated UserModule from the serviceManager */
	protected ClientFacade getUserModule() {
		return clientFacade;
	}

	public CalendarOptions getCalendarOptions() {
		User user;
		try
		{
			user = getUser();
		}
		catch (RaplaException ex) {
			// Use system settings if an error occurs
			user = null;
		}
		return getCalendarOptions( user);
	}

	/** returns if the session user is admin */
	final public boolean isAdmin() {
		try {
			return getUser().isAdmin();
		} catch (RaplaException ex) {
		}
		return false;
	}

	final public boolean isModifyPreferencesAllowed() {
		try {
			User user = getUser();
			return isModifyPreferencesAllowed( user);
		} catch (RaplaException ex) {
		}
		return false;
	}

	protected Date getStartDate(CalendarModel model) throws RaplaException
	{
		final RaplaFacade raplaFacade = getFacade();
		return getStartDate(model, raplaFacade, getUser());
	}

	public ClientFacade getClientFacade()
	{
		return clientFacade;
	}

	protected PopupContext createPopupContext(Component parent, Point p)
    {
        return new SwingPopupContext(parent, p);
    }

    public RaplaCalendar createRaplaCalendar(DateRenderer dateRenderer, IOInterface service) {
        RaplaCalendar cal = new RaplaCalendar( getI18n().getLocale(),getRaplaLocale().getTimeZone());
        cal.setDateRenderer(dateRenderer);
        addCopyPaste(cal.getDateField(), getI18n(), getRaplaLocale(), service, getLogger());
        return cal;
    }

    static Color NON_WORKTIME = new Color(0xcc, 0xcc, 0xcc);

    final protected TimeRenderer getTimeRenderer() {
// BJO 00000070
        final int start = getCalendarOptions().getWorktimeStartMinutes();
        final int end = getCalendarOptions().getWorktimeEndMinutes();
// BJO 00000070
        return new TimeRenderer() {
            public Color getBackgroundColor( int hourOfDay, int minute )
            {
// BJO 00000070
                int worktime = hourOfDay * 60 + minute;     
// BJO 00000070 
                if ( start >= end)
                {
// BJO 00000070 
                    if ( worktime >= end && worktime < start)
// BJO 00000070 
                    {
                        return NON_WORKTIME;
                    }
                }
// BJO 00000070 
                else if ( worktime < start || worktime >= end) {
// BJO 00000070 
                    return NON_WORKTIME;
                }
                return null;
            }

            public String getToolTipText( int hourOfDay, int minute )
            {
                return null;
            }
            
            public String getDurationString(int durationInMinutes) {
				if ( durationInMinutes > 0 )
	            {
					int hours =  durationInMinutes / 60; 
	            	int minutes =  durationInMinutes  % 60;
	            	if ( hours == 0)
	            	{
	            		return "("+minutes + " " + getString("minutes.abbreviation") + ")";
	            	}

	            	if ( minutes % 30 != 0)
	            	{
	            		return "";
	            	}
		            StringBuilder builder = new StringBuilder();
	            	builder.append(" (");
					
					if ( hours > 0)
					{
						builder.append(hours );
					}
					if ( minutes % 60 != 0)
					{
						char c = 189; // 1/2
						builder.append(c);
					}
					if ( minutes % 30 == 0)
					{
						builder.append( " " + getString((hours == 1 && minutes % 60 == 0 ? "hour.abbreviation" :"hours.abbreviation")) + ")");
					}
		            return builder.toString();
	            }
				return "";
			}

        };
    }


    public RaplaTime createRaplaTime(IOInterface service) {
        RaplaTime cal = new RaplaTime( getI18n().getLocale(), getRaplaLocale().getTimeZone());
        cal.setTimeRenderer( getTimeRenderer() );
        int rowsPerHour =getCalendarOptions().getRowsPerHour() ;
        cal.setRowsPerHour( rowsPerHour );
        addCopyPaste(cal.getTimeField(), getI18n(), getRaplaLocale(), service, getLogger());
        return cal;
    }
    
    public Component getMainComponent() {
        return  mainComponent;
    }
    
    public static void addCopyPaste(final JComponent component, RaplaResources i18n, final RaplaLocale raplaLocale, final IOInterface service, final Logger logger) {
        ActionListener pasteListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                paste(component, e, service, logger);
            }
        };
        ActionListener copyListener = new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
            	copy(component, e, service, raplaLocale);
            }
        };
    	final JPopupMenu menu = new JPopupMenu();
        {
            final JMenuItem copyItem = new JMenuItem();
           
			copyItem.addActionListener( copyListener);
            copyItem.setText(i18n.getString("copy"));
            
            menu.add(copyItem);
        }
        {
            final JMenuItem pasteItem = new JMenuItem();
        	pasteItem.addActionListener( pasteListener);
            pasteItem.setText(i18n.getString("paste"));
            menu.add(pasteItem);
        }

        component.add(menu);
        component.addMouseListener(new MouseAdapter()
        {
            private void showMenuIfPopupTrigger(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    menu.show(component,e.getX() + 3, e.getY() + 3);
                }
            }
    
            public void mousePressed(MouseEvent e) {
                showMenuIfPopupTrigger(e);
            }
    
            public void mouseReleased(MouseEvent e) {
                showMenuIfPopupTrigger(e);
            }
        }
        );
        
		component.registerKeyboardAction(copyListener,i18n.getString("copy"),COPY_STROKE,JComponent.WHEN_FOCUSED);
        component.registerKeyboardAction(pasteListener,i18n.getString("paste"),PASTE_STROKE,JComponent.WHEN_FOCUSED);
    }

    public static KeyStroke COPY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_C,ActionEvent.CTRL_MASK,false);
    public static KeyStroke CUT_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_X,ActionEvent.CTRL_MASK,false);
    public static KeyStroke PASTE_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_V,ActionEvent.CTRL_MASK,false);

	protected static void copy(final JComponent component, ActionEvent e, final IOInterface service, final RaplaLocale raplaLocale) {
		final Transferable transferable;
		if ( component instanceof JTextComponent)
		{
			String selectedText = ((JTextComponent)component).getSelectedText();
			transferable = new StringSelection(selectedText);
		}
		else if ( component instanceof JTable)
		{
			JTable table = (JTable)component;
			transferable = getSelectedContent(table, raplaLocale);
		}
		else
		{
			transferable = new StringSelection(component.toString());
		}

		if ( transferable != null)
		{
			try
			{
			    if (service != null) {
			        service.setContents(transferable, null);
			    }
			    else
			    {
			        Action action = component.getActionMap().get(DefaultEditorKit.copyAction);
					if ( action != null)
					{
							action.actionPerformed(e);
					}
			    }
			}
			catch (AccessControlException ex)
			{
				clipboard.set( transferable);
			}

		}
	}


	
	static ThreadLocal<Transferable> clipboard =  new ThreadLocal<Transferable>();

	/** Code from
	http://www.javaworld.com/javatips/jw-javatip77.html
	*/
	  private static final String LINE_BREAK = "\n"; 
      private static final String CELL_BREAK = "\t"; 
    
	
	 private static StringSelection getSelectedContent(JTable table, final RaplaLocale raplaLocale) { 
         int numCols=table.getSelectedColumnCount(); 
         int[] rowsSelected=table.getSelectedRows(); 
         int[] colsSelected=table.getSelectedColumns(); 
//         int numRows=table.getSelectedRowCount(); 
//         if (numRows!=rowsSelected[rowsSelected.length-1]-rowsSelected[0]+1 || numRows!=rowsSelected.length || 
//                         numCols!=colsSelected[colsSelected.length-1]-colsSelected[0]+1 || numCols!=colsSelected.length) {
//        	 
//        	 JOptionPane.showMessageDialog(null, "Invalid Copy Selection", "Invalid Copy Selection", JOptionPane.ERROR_MESSAGE);
//        	 return null; 
//         } 
     
         StringBuffer excelStr=new StringBuffer(); 
         for (int row:rowsSelected) 
         { 
        	 int j=0;
        	 for (int col:colsSelected) 
        	 { 
        		 Object value = table.getValueAt(row, col);
        		 String formated;
        		 Class<?> columnClass = table.getColumnClass( col);
        		 boolean isDate = columnClass.isAssignableFrom( java.util.Date.class);
        		 if ( isDate)
        		 {
        			 SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        			 format.setTimeZone( raplaLocale.getTimeZone());
        			 if ( value instanceof java.util.Date)
        			 {
        				 String timestamp = format.format(   (java.util.Date)value);
        				 formated = timestamp;
        			 }
        			 else
        			 {
        				 String escaped = escape(value);
            			 formated = escaped;
        			 }
        		 }
        		 else
        		 {
        			 String escaped = escape(value);
        			 formated = escaped;
        		 }
        		 excelStr.append( formated );
        		 boolean isLast = j==numCols-1;
        		 if (!isLast) { 
        			 excelStr.append(CELL_BREAK); 
        		 } 
        		 j++;
        	 } 
        	 excelStr.append(LINE_BREAK); 
         } 
         
         String string = excelStr.toString();
         StringSelection sel  = new StringSelection(string); 
         return sel;
	 } 
	 	
	 
	 private static String escape(Object cell) { 
         return cell.toString().replace(LINE_BREAK, " ").replace(CELL_BREAK, " "); 
	 }
	 /** Code End	 */


	protected static void paste(final JComponent component, ActionEvent e, final IOInterface service, Logger logger) {
		try
		{
	        if (service != null) {
				final Transferable transferable = service.getContents( null);
				Object transferData;
				try {
					if ( transferable.isDataFlavorSupported(DataFlavor.stringFlavor))
					{
						transferData = transferable.getTransferData(DataFlavor.stringFlavor);
						if ( transferData != null)
						{
							if ( component instanceof JTextComponent)
							{
								((JTextComponent)component).replaceSelection( transferData.toString());
							}
							if ( component instanceof JTable)
							{
								// Paste currently not supported
							}
						}
					}
					else if ( transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
					{
						transferData = transferable.getTransferData(DataFlavor.javaFileListFlavor);
						if ( transferData != null)
						{
							if ( component instanceof JTextComponent)
							{
								List<File> fileList = (List<File>) transferData;
								if ( fileList.size() > 0)
								{
									File file = fileList.get(0);
									final URI uri = file.getAbsoluteFile().toURI();
									((JTextComponent)component).replaceSelection( uri.toString());
								}
							}
							if ( component instanceof JTable)
							{
								// Paste currently not supported
							}
						}
					}

				} catch (Exception ex) {
					logger.warn(ex.getMessage(),ex);
				}
	           
	        } 
	        else
	        {
	            Action action = component.getActionMap().get(DefaultEditorKit.pasteAction);
				if ( action != null)
				{
					action.actionPerformed(e);
				}
	        }
		}
		catch (AccessControlException ex) 
		{
			Transferable transferable =clipboard.get();
        	if ( transferable != null)
        	{
				if ( component instanceof JTextComponent)
            	{
					Object transferData;
					try {
						transferData = transferable.getTransferData(DataFlavor.stringFlavor);
						((JTextComponent)component).replaceSelection( transferData.toString());
					} catch (Exception e1) {
					    if(logger!=null)
					        logger.error( e1.getMessage(),e1);
					}
            	}
        	}	        
		}

	}

	

    
   
   
}
