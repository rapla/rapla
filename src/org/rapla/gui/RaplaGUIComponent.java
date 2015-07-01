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
package org.rapla.gui;

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
import java.security.AccessControlException;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Map;

import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.JTextComponent;

import org.rapla.client.ClientService;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.calendar.RaplaTime;
import org.rapla.components.calendar.TimeRenderer;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.entities.DependencyException;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.internal.action.AppointmentAction;
import org.rapla.gui.toolkit.ErrorDialog;
import org.rapla.gui.toolkit.FrameControllerList;
import org.rapla.storage.RaplaNewVersionException;
import org.rapla.storage.RaplaSecurityException;
import org.rapla.storage.dbrm.RaplaConnectException;
import org.rapla.storage.dbrm.RaplaRestartingException;
import org.rapla.storage.dbrm.WrongRaplaVersionException;

/**
    Base class for most components in the gui package. Eases
    access to frequently used services, e.g. {@link org.rapla.components.xmlbundle.I18nBundle}.
    It also provides some methods for Exception displaying.
 */
public class RaplaGUIComponent extends RaplaComponent
{

    public RaplaGUIComponent(RaplaContext context)  {
        super(context);
    }

    /** lookup FrameControllerList from the context */
    final protected FrameControllerList getFrameList() {
        return getService(FrameControllerList.class);
    }

    /** Creates a new ErrorDialog with the specified owner and displays the exception
    @param ex the exception that should be displayed.
    @param owner the exception that should be displayed. Can be null, but providing
    a parent-component will lead to a more appropriate display.
	*/
	public void showException(Exception ex,Component owner) {
	    RaplaContext context = getContext();
		Logger logger = getLogger();
		showException(ex, owner, context, logger);
	}
	
	static public void showException(Throwable ex, Component owner,
			RaplaContext context, Logger logger) {
		if ( ex instanceof RaplaConnectException)
	    {
	        String message = ex.getMessage();
	        Throwable cause = ex.getCause();
            String additionalInfo = "";
            if ( cause != null)
            {
            	additionalInfo = " " + cause.getClass()  + ":" + cause.getMessage();
            }
            	
			logger.warn(message + additionalInfo);
	        if ( ex instanceof RaplaRestartingException)
	        {
	            return;
	        }
	        try {
	            ErrorDialog dialog = new ErrorDialog(context);
	            dialog.showWarningDialog( message, owner);
	        } catch (RaplaException e) {
	    	} catch (Throwable e) {
	    		logger.error(e.getMessage(), e);
	    	}
	        return;
	    }
	    try {
	        ErrorDialog dialog = new ErrorDialog(context);
	        if (ex instanceof DependencyException) {
	            dialog.showWarningDialog( getHTML( (DependencyException)ex ), owner);
	        }
	        else if (isWarningOnly(ex)) {
	             dialog.showWarningDialog( ex.getMessage(), owner);
	        } else {
	            dialog.showExceptionDialog(ex,owner);
	        }
	    } catch (RaplaException ex2) {
	        logger.error(ex2.getMessage(),ex2);
		} catch (Throwable ex2) {
			logger.error(ex2.getMessage(),ex2);
		}
	}
	
	static public boolean isWarningOnly(Throwable ex) {
		return ex instanceof RaplaNewVersionException  || ex instanceof RaplaSecurityException || ex instanceof WrongRaplaVersionException || ex instanceof RaplaConnectException;
	}
	
	static private String getHTML(DependencyException ex){
        StringBuffer buf = new StringBuffer();
        buf.append(ex.getMessage()+":");
        buf.append("<br><br>");
        Iterator<String> it = ex.getDependencies().iterator();
        int i = 0;
        while (it.hasNext()) {
            Object obj = it.next();
            buf.append((++i));
            buf.append(") ");
            
           
            buf.append( obj);

            buf.append("<br>");
            if (i == 30 && it.hasNext()) { 
                buf.append("... " + (ex.getDependencies().size() - 30) + " more"); 
                break;
            }
        }
        return buf.toString();
    }

	 /** Creates a new ErrorDialog with the specified owner and displays the waring */
    public void showWarning(String warning,Component owner) {
        RaplaContext context = getContext();
    	Logger logger = getLogger();
		showWarning(warning, owner, context, logger);
    }

	public static void showWarning(String warning, Component owner,	RaplaContext context, Logger logger) {
		try {
			ErrorDialog dialog = new ErrorDialog(context);
            dialog.showWarningDialog(warning,owner);
        } catch (RaplaException ex2) {
        	logger.error(ex2.getMessage(),ex2);
        }
	}


    public RaplaCalendar createRaplaCalendar() {
        RaplaCalendar cal = new RaplaCalendar( getI18n().getLocale(),getRaplaLocale().getTimeZone());
        cal.setDateRenderer(getDateRenderer());
        addCopyPaste(cal.getDateField());
        return cal;
    }

    /** lookup DateRenderer from the serviceManager */
    final protected DateRenderer getDateRenderer() {
        return  getService(DateRenderer.class);
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

    @Deprecated 
    public AppointmentAction createAppointmentAction(Component component, Point p)
    {
        return new AppointmentAction(getContext(),component, p);
    }

    public RaplaTime createRaplaTime() {
        RaplaTime cal = new RaplaTime( getI18n().getLocale(), getRaplaLocale().getTimeZone());
        cal.setTimeRenderer( getTimeRenderer() );
        int rowsPerHour =getCalendarOptions().getRowsPerHour() ;
        cal.setRowsPerHour( rowsPerHour );
        addCopyPaste(cal.getTimeField());
        return cal;
    }
    

    public Map<Object,Object> getSessionMap() {
        return  getService( ClientService.SESSION_MAP);
    }

    protected InfoFactory getInfoFactory() {
        return getService( InfoFactory.class );
    }
    
    /** calls getI18n().getIcon(key) */
    final public ImageIcon getIcon(String key) {
        return getI18n().getIcon(key);
    }


    public EditController getEditController() {
        return  getService( EditController.class );
    }

    protected ReservationController getReservationController() {
        return getService( ReservationController.class );
    }

    public Component getMainComponent() {
        return  getService(ClientService.MAIN_COMPONENT);
    }
    
    public void addCopyPaste(final JComponent component) {
        ActionListener pasteListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                paste(component, e);
            }
        };
        ActionListener copyListener = new ActionListener()
        {
            public void actionPerformed(ActionEvent e) {
            	copy(component, e);
            }
        };
    	final JPopupMenu menu = new JPopupMenu();
        {
            final JMenuItem copyItem = new JMenuItem();
           
			copyItem.addActionListener( copyListener);
            copyItem.setText(getString("copy"));
            
            menu.add(copyItem);
        }
        {
            final JMenuItem pasteItem = new JMenuItem();
        	pasteItem.addActionListener( pasteListener);
            pasteItem.setText(getString("paste"));
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
        
		component.registerKeyboardAction(copyListener,getString("copy"),COPY_STROKE,JComponent.WHEN_FOCUSED);
        component.registerKeyboardAction(pasteListener,getString("paste"),PASTE_STROKE,JComponent.WHEN_FOCUSED);
    }

    public static KeyStroke COPY_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_C,ActionEvent.CTRL_MASK,false);
    public static KeyStroke CUT_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_X,ActionEvent.CTRL_MASK,false);
    public static KeyStroke PASTE_STROKE = KeyStroke.getKeyStroke(KeyEvent.VK_V,ActionEvent.CTRL_MASK,false);
  
    protected IOInterface getIOService() 
    {
        try {
            return getService( IOInterface.class);
        } catch (Exception e) {
            return null;
        }
    }

	protected void copy(final JComponent component, ActionEvent e) {
		final Transferable transferable;
		if ( component instanceof JTextComponent)
		{
			String selectedText = ((JTextComponent)component).getSelectedText();
			transferable = new StringSelection(selectedText);
		}
		else if ( component instanceof JTable)
		{
			JTable table = (JTable)component;
			transferable = getSelectedContent(table);
		}
		else
		{
			transferable = new StringSelection(component.toString());
		}

		if ( transferable != null)
		{
			try
			{
				final IOInterface service = getIOService();
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
    
	
	 private StringSelection getSelectedContent(JTable table) { 
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
        			 format.setTimeZone( getRaplaLocale().getTimeZone());
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
	 	
	 
	 private String escape(Object cell) { 
         return cell.toString().replace(LINE_BREAK, " ").replace(CELL_BREAK, " "); 
	 }
	 /** Code End	 */ 

	
	protected void paste(final JComponent component, ActionEvent e) {
		try
		{
			final IOInterface service = getIOService();
	        if (service != null) {
	            final Transferable transferable = service.getContents( null);
	            Object transferData;
	            try {
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
	            } catch (Exception ex) {
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
						getLogger().error( e1.getMessage(),e1);
					}
            	}
        	}	        
		}

	}

	

    
   
   
}
