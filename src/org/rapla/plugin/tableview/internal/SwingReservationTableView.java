package org.rapla.plugin.tableview.internal;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.StringTokenizer;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.tablesorter.TableSorter;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.Container;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.internal.ConfigTools.RaplaReaderImpl;
import org.rapla.gui.MenuContext;
import org.rapla.gui.MenuFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.ReservationController;
import org.rapla.gui.SwingCalendarView;
import org.rapla.gui.VisibleTimeInterval;
import org.rapla.gui.internal.common.InternMenus;
import org.rapla.gui.toolkit.MenuInterface;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaPopupMenu;
import org.rapla.plugin.abstractcalendar.IntervalChooserPanel;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.ReservationTableColumn;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

public class SwingReservationTableView extends RaplaGUIComponent implements SwingCalendarView, Printable, VisibleTimeInterval
{
    ReservationTableModel reservationTableModel;

    JTable table;
    CalendarModel model;
    IntervalChooserPanel dateChooser;
    JScrollPane scrollpane;
    JComponent container;
    TableSorter sorter;

    CopyListener copyListener = new CopyListener();
    CopyListener cutListener = new CopyListener();
	
    public SwingReservationTableView( RaplaContext context, final CalendarModel model, final boolean editable ) throws RaplaException
    {
        super( context );
        cutListener.setCut(true);
        table = new JTable() {
            private static final long serialVersionUID = 1L;
            
            public String getToolTipText(MouseEvent e) 
            {
                if (!editable)
                    return null;
                int rowIndex = rowAtPoint( e.getPoint() );
                Reservation reservation = reservationTableModel.getReservationAt( sorter.modelIndex( rowIndex ));
                return getInfoFactory().getToolTip( reservation );
            }
        };
        
        scrollpane = new JScrollPane( table);
        if ( editable )
        {
        	container = new JPanel();
        	container.setLayout( new BorderLayout());
        	container.add( scrollpane, BorderLayout.CENTER);
        	JPanel extensionPanel = new JPanel();
        	extensionPanel.setLayout( new BoxLayout(extensionPanel, BoxLayout.X_AXIS));
        	container.add( extensionPanel, BorderLayout.SOUTH);
            scrollpane.setPreferredSize( new Dimension(600,800));
        	PopupTableHandler popupHandler = new PopupTableHandler();
        	scrollpane.addMouseListener( popupHandler);
        	table.addMouseListener( popupHandler );
        	Collection< ? extends SummaryExtension> reservationSummaryExtensions  = getContainer().lookupServicesFor(TableViewExtensionPoints.RESERVATION_TABLE_SUMMARY);
     		for ( SummaryExtension summary:reservationSummaryExtensions)
     		{
     			summary.init(table, extensionPanel);
     		}
        }
        else
        {
            Dimension size = table.getPreferredSize();
            scrollpane.setBounds( 0,0,600, (int)size.getHeight());
            container = scrollpane;
        }
        this.model = model;
        //Map<?,?> map = getContainer().lookupServicesFor(RaplaExtensionPoints.APPOINTMENT_STATUS);
        //Collection<AppointmentStatusFactory> appointmentStatusFactories = (Collection<AppointmentStatusFactory>) map.values();
        User user = model.getUser();
       	List<RaplaTableColumn<Reservation>> reservationColumnPlugins = TableConfig.loadColumns(getContainer(), "events", TableViewExtensionPoints.RESERVATION_TABLE_COLUMN, user);
       	reservationTableModel = new ReservationTableModel( getLocale(),getI18n(), reservationColumnPlugins );
        ReservationTableModel tableModel = reservationTableModel;
        sorter = createAndSetSorter(model, table, TableViewPlugin.EVENTS_SORTING_STRING_OPTION, tableModel);

        int column = 0;
        for (RaplaTableColumn<?> col: reservationColumnPlugins)
        {
        	col.init(table.getColumnModel().getColumn(column  ));
        	column++;	
        }
        
        
        table.setColumnSelectionAllowed( true );
        table.setRowSelectionAllowed( true);
        table.getTableHeader().setReorderingAllowed(false);
       
    	table.registerKeyboardAction(copyListener,getString("copy"),COPY_STROKE,JComponent.WHEN_FOCUSED);
    	table.registerKeyboardAction(cutListener,getString("cut"),CUT_STROKE,JComponent.WHEN_FOCUSED);
    	
        dateChooser = new IntervalChooserPanel( context, model);
        dateChooser.addDateChangeListener( new DateChangeListener() {
            public void dateChanged( DateChangeEvent evt )
            {
                try {
                    update(  );
                } catch (RaplaException ex ){
                    showException( ex, getComponent());
                }
            }
        });

        reservationTableModel.setReservations( model.getReservations() );

        
        Listener listener = new Listener();
      	table.getSelectionModel().addListSelectionListener( listener);
      	table.addFocusListener( listener);
    }
    
    
    private final class CopyListener implements ActionListener {
        boolean cut;
        public void actionPerformed(ActionEvent evt) 
		{
	        List<Reservation> selectedEvents = getSelectedEvents();
	        Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
	        try {
	            ReservationController reservationController = getReservationController();
                if ( isCut())
	            {
                    reservationController.cutReservations(selectedEvents, markedAllocatables);
	            }
	            else
	            {
	                reservationController.copyReservations(selectedEvents, markedAllocatables);
	            }
            } catch (RaplaException ex) {
                showException(ex, getComponent());
            }
	        copy(table, evt);            
		}
        
        public boolean isCut() {
            return cut;
        }
        
        public void setCut(boolean cut) {
            this.cut = cut;
        }
    }

    class Listener implements ListSelectionListener, FocusListener
    {
	   public void valueChanged(ListSelectionEvent e) {
		   updateEditMenu();
	   }

	   public void focusGained(FocusEvent e) {
		   updateEditMenu();
		}

		public void focusLost(FocusEvent e) {
		}

    }

    public static TableSorter createAndSetSorter(final CalendarModel model, final JTable table, final String sortingStringOptionName, TableModel tableModel) {
        final TableSorter sorter =  new TableSorter( tableModel, table.getTableHeader());
        String sorting = model.getOption(sortingStringOptionName);
        if ( sorting != null)
        {
           Enumeration<Object> e = new StringTokenizer( sorting,";", false);
           for (Object stringToCast:Collections.list(e))
           {
               String string = (String) stringToCast;
               int length = string.length();
               int column = Integer.parseInt(string.substring(0,length-1));
               char order = string.charAt( length-1);
               if ( column < tableModel.getColumnCount())
               {
                   sorter.setSortingStatus( column, order == '-' ? TableSorter.DESCENDING : TableSorter.ASCENDING);
               }
           }
        }
        sorter.addTableModelListener(new TableModelListener() {
            
            public void tableChanged(TableModelEvent e) 
            {
                StringBuffer buf = new StringBuffer();
                for ( int i=0;i<table.getColumnCount();i++)
                {
                    int sortingStatus = sorter.getSortingStatus( i);
                    if (sortingStatus == TableSorter.ASCENDING)
                    {
                        buf.append(i + "+;");
                    }
                    if (sortingStatus == TableSorter.DESCENDING)
                    {
                        buf.append(i + "-;");
                    }
                }
                String sortingString = buf.toString();
                ((CalendarSelectionModel)model).setOption(sortingStringOptionName, sortingString.length() > 0 ? sortingString : null);
            }
        });
        table.setModel(  sorter );
        return sorter;
    }
    
    protected void updateEditMenu() {
		List<Reservation> selectedEvents = getSelectedEvents();
		if ( selectedEvents.size() == 0 )
		{
			return;
		}
		RaplaMenu editMenu =  getService(InternMenus.EDIT_MENU_ROLE);
		RaplaMenu newMenu = getService(InternMenus.NEW_MENU_ROLE);

		editMenu.removeAllBetween("EDIT_BEGIN", "EDIT_END");
		newMenu.removeAll();
		Point p  = null;
		try {
			updateMenu(editMenu,newMenu, p);
			boolean canUserAllocateSomething = canUserAllocateSomething(getUser());
			boolean enableNewMenu = newMenu.getMenuComponentCount() > 0 && canUserAllocateSomething;
			newMenu.setEnabled(enableNewMenu);
			editMenu.setEnabled(canUserAllocateSomething(getUser()));
		} catch (RaplaException ex) {
			showException (ex,getComponent());
		}
	}
    
    public void update() throws RaplaException
    {
        reservationTableModel.setReservations( model.getReservations() );
        dateChooser.update();
    }

    public JComponent getDateSelection()
    {
        return dateChooser.getComponent();
    }

    public void scrollToStart()
    {

    }

    public JComponent getComponent()
    {
    	return container;
    }
    
    protected void updateMenu(MenuInterface editMenu,MenuInterface newMenu, Point p) throws RaplaException {
		List<Reservation> selectedEvents = getSelectedEvents();
        Reservation focusedObject = null;
        if ( selectedEvents.size() == 1) {
            focusedObject = selectedEvents.get( 0);
        }
        MenuContext menuContext = new MenuContext( getContext(), focusedObject,getComponent(),p);
        menuContext.setSelectedObjects( selectedEvents);

        // add the new reservations wizards
        MenuFactory menuFactory = getService(MenuFactory.class);
        menuFactory.addReservationWizards( newMenu, menuContext, null);
        
        // add the edit methods
        if ( selectedEvents.size() != 0) {
            {
                final JMenuItem copyItem = new JMenuItem();
            	copyItem.addActionListener( cutListener);
            	copyItem.setText(getString("cut"));
            	copyItem.setIcon(  getIcon("icon.cut"));
            	editMenu.insertAfterId(copyItem, "EDIT_BEGIN");
            }
            {
                final JMenuItem copyItem = new JMenuItem();
                copyItem.addActionListener( copyListener);
                copyItem.setText(getString("copy"));
                copyItem.setIcon(  getIcon("icon.copy"));
                editMenu.insertAfterId(copyItem, "EDIT_BEGIN");
            }

            menuFactory.addObjectMenu( editMenu, menuContext, "EDIT_BEGIN");
        } 

	}

    List<Reservation> getSelectedEvents() {
        int[] rows = table.getSelectedRows();
        List<Reservation> selectedEvents = new ArrayList<Reservation>();
        for (int i=0;i<rows.length;i++)
        {
            Reservation reservation =reservationTableModel.getReservationAt( sorter.modelIndex(rows[i]) );
            selectedEvents.add( reservation);
        }
        return selectedEvents;
    }

    class PopupTableHandler extends MouseAdapter {

        void showPopup(MouseEvent me) {
        	 try
             {
	        	RaplaPopupMenu menu= new RaplaPopupMenu();
	            Point p = new Point(me.getX(), me.getY());
	            RaplaMenu newMenu = new RaplaMenu("EDIT_BEGIN");
	            newMenu.setText(getString("new"));
	            menu.add(newMenu);
	            boolean canUserAllocateSomething = canUserAllocateSomething(getUser());
	            updateMenu(menu,newMenu, p);
	            boolean enableNewMenu = newMenu.getMenuComponentCount() > 0 && canUserAllocateSomething;
	            newMenu.setEnabled(enableNewMenu);
            	menu.show( table, p.x, p.y);
            } catch (RaplaException ex) {
            	showException (ex,getComponent());
            }
        }

		

        /** Implementation-specific. Should be private.*/
        public void mousePressed(MouseEvent me) {
            if (me.isPopupTrigger())
                showPopup(me);
        }
        /** Implementation-specific. Should be private.*/
        public void mouseReleased(MouseEvent me) {
            if (me.isPopupTrigger())
                showPopup(me);
        }
        /** we want to edit the reservation on double click*/
        public void mouseClicked(MouseEvent me) {
            List<Reservation> selectedEvents = getSelectedEvents();
            if (me.getClickCount() > 1  && selectedEvents.size() == 1 )
            {
                Reservation reservation = selectedEvents.get( 0);
                if (!canModify( reservation ))
                {
                    return;
                }
                try {
                    getReservationController().edit( reservation );
                } catch (RaplaException ex) {
                    showException (ex,getComponent());
                }
            }
        }
    }

    Printable printable = null;
    /**
     * @see java.awt.print.Printable#print(java.awt.Graphics, java.awt.print.PageFormat, int)
     */
    public int print(Graphics graphics, PageFormat format, int page) throws PrinterException {
    	MessageFormat f1 = new MessageFormat( model.getNonEmptyTitle());
    	Printable  printable = table.getPrintable( JTable.PrintMode.FIT_WIDTH,f1, null );
        return printable.print( graphics, format, page);
    }
    
	public TimeInterval getVisibleTimeInterval() {
		return new TimeInterval(model.getStartDate(), model.getEndDate());
	}


 }
