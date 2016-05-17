package org.rapla.plugin.tableview.client.swing;

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
import java.util.Set;
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
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;

import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.swing.InfoFactory;
import org.rapla.client.swing.SwingMenuContext;
import org.rapla.client.swing.MenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.VisibleTimeInterval;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.MenuInterface;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.tablesorter.TableSorter;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.client.swing.IntervalChooserPanel;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;
import org.rapla.plugin.tableview.client.swing.extensionpoints.SummaryExtension;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.scheduler.Promise;
import org.rapla.server.PromiseSynchroniser;
import org.rapla.storage.PermissionController;

public class SwingReservationTableView extends RaplaGUIComponent implements SwingCalendarView, Printable, VisibleTimeInterval
{
    private final TableConfig.TableConfigLoader tableConfigLoader;

    ReservationTableModel reservationTableModel;

    JTable table;
    CalendarModel model;
    IntervalChooserPanel dateChooser;
    JScrollPane scrollpane;
    JComponent container;
    TableSorter sorter;

    CopyListener copyListener = new CopyListener();
    CopyListener cutListener = new CopyListener();
    
    private final MenuFactory menuFactory;

    private final EditController editController;
    private final ReservationController reservationController;

    private final RaplaImages raplaImages;

    private final DialogUiFactoryInterface dialogUiFactory;

    private final PermissionController permissionController;

    private final IOInterface ioInterface;

    private final RaplaMenuBarContainer menuBar;

    public SwingReservationTableView(RaplaMenuBarContainer menuBar, ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
            final CalendarModel model, final Set<ReservationSummaryExtension> reservationSummaryExtensions, final boolean editable, boolean printing,
            TableConfig.TableConfigLoader tableConfigLoader, MenuFactory menuFactory, EditController editController, ReservationController reservationController,
            final InfoFactory infoFactory, RaplaImages raplaImages, IntervalChooserPanel dateChooser, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.menuBar = menuBar;
        this.tableConfigLoader = tableConfigLoader;
        this.menuFactory = menuFactory;
        this.editController = editController;
        this.reservationController = reservationController;
        this.raplaImages = raplaImages;
        this.dialogUiFactory = dialogUiFactory;
        this.permissionController = facade.getRaplaFacade().getPermissionController();
        this.ioInterface = ioInterface;
        cutListener.setCut(true);
        table = new JTable() {
            private static final long serialVersionUID = 1L;
            
            public String getToolTipText(MouseEvent e) 
            {
                if (!editable)
                    return null;
                int rowIndex = rowAtPoint( e.getPoint() );
                Reservation reservation = reservationTableModel.getReservationAt( sorter.modelIndex( rowIndex ));
                return infoFactory.getToolTip( reservation );
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

        List<RaplaTableColumn<Reservation,TableColumn>> reservationColumnConfigured = tableConfigLoader.loadColumns("events", getUser());
        reservationTableModel = new ReservationTableModel( getLocale(), i18n, reservationColumnConfigured );
        ReservationTableModel tableModel = reservationTableModel;
        sorter = createAndSetSorter(model, table, TableViewPlugin.EVENTS_SORTING_STRING_OPTION, tableModel);

        int column = 0;
        for (RaplaTableColumn<Reservation,TableColumn> col: reservationColumnConfigured)
        {
        	col.init(table.getColumnModel().getColumn(column  ));
        	column++;	
        }
        
        
        table.setColumnSelectionAllowed( true );
        table.setRowSelectionAllowed( true);
        table.getTableHeader().setReorderingAllowed(false);
       
    	table.registerKeyboardAction(copyListener,getString("copy"),COPY_STROKE,JComponent.WHEN_FOCUSED);
    	table.registerKeyboardAction(cutListener,getString("cut"),CUT_STROKE,JComponent.WHEN_FOCUSED);
    	
        this.dateChooser = dateChooser;
        dateChooser.addDateChangeListener( new DateChangeListener() {
            public void dateChanged( DateChangeEvent evt )
            {
                try {
                    PromiseSynchroniser.waitForWithRaplaException(update(), 10000);
                } catch (RaplaException ex ){
                    SwingReservationTableView.this.dialogUiFactory.showException( ex, new SwingPopupContext(getComponent(), null));
                }
            }
        });

        final Promise<Collection<Reservation>> promise = model.queryReservations(model.getTimeIntervall());
        promise.thenAccept((reservations) ->
        {
            reservationTableModel.setReservations(reservations.toArray(new Reservation[] {}));
            Listener listener = new Listener();
            table.getSelectionModel().addListSelectionListener(listener);
            table.addFocusListener(listener);
        });
    }
    

    
    private final class CopyListener implements ActionListener {
        boolean cut;
        public void actionPerformed(ActionEvent evt) 
		{
	        List<Reservation> selectedEvents = getSelectedEvents();
	        Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
	        try {
                if ( isCut())
	            {
                    reservationController.cutReservations(selectedEvents, markedAllocatables);
	            }
	            else
	            {
	                reservationController.copyReservations(selectedEvents, markedAllocatables);
	            }
            } catch (RaplaException ex) {
                dialogUiFactory.showException(ex, new SwingPopupContext(getComponent(), null));
            }
	        copy(table, evt, ioInterface, getRaplaLocale());            
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
		RaplaMenu editMenu =  menuBar.getEditMenu();
		RaplaMenu newMenu = menuBar.getViewMenu();

		editMenu.removeAllBetween("EDIT_BEGIN", "EDIT_END");
		newMenu.removeAll();
		Point p  = null;
		try {
			updateMenu(editMenu,newMenu, p);
			final User user = getUser();
            final RaplaFacade raplaFacade = getFacade();
            boolean canUserAllocateSomething = permissionController.canUserAllocateSomething(user);
			boolean enableNewMenu = newMenu.getMenuComponentCount() > 0 && canUserAllocateSomething;
			newMenu.setEnabled(enableNewMenu);
			editMenu.setEnabled(permissionController.canUserAllocateSomething(user));
		} catch (RaplaException ex) {
		    dialogUiFactory.showException (ex,new SwingPopupContext(getComponent(), null));
		}
	}
    
    public Promise<Void> update() 
    {
        final Promise<Collection<Reservation>> promise = model.queryReservations(model.getTimeIntervall());
        final Promise<Void> voidPromise = promise.thenAccept((reservations) ->
        {
            reservationTableModel.setReservations(reservations.toArray(new Reservation[] {}));
            dateChooser.update();
        });
        return voidPromise;
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
        SwingMenuContext menuContext = new SwingMenuContext(  focusedObject, new SwingPopupContext(getComponent(),p), null, p);
        menuContext.setSelectedObjects( selectedEvents);

        // add the new reservations wizards
        menuFactory.addReservationWizards( newMenu, menuContext, null);
        
        // add the edit methods
        if ( selectedEvents.size() != 0) {
            {
                final JMenuItem copyItem = new JMenuItem();
            	copyItem.addActionListener( cutListener);
            	copyItem.setText(getString("cut"));
            	copyItem.setIcon(  raplaImages.getIconFromKey("icon.cut"));
            	editMenu.insertAfterId(copyItem, "EDIT_BEGIN");
            }
            {
                final JMenuItem copyItem = new JMenuItem();
                copyItem.addActionListener( copyListener);
                copyItem.setText(getString("copy"));
                copyItem.setIcon(  raplaImages.getIconFromKey("icon.copy"));
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
	            boolean canUserAllocateSomething = permissionController.canUserAllocateSomething(getUser());
	            updateMenu(menu,newMenu, p);
	            boolean enableNewMenu = newMenu.getMenuComponentCount() > 0 && canUserAllocateSomething;
	            newMenu.setEnabled(enableNewMenu);
            	menu.show( table, p.x, p.y);
            } catch (RaplaException ex) {
                dialogUiFactory.showException (ex,new SwingPopupContext(getComponent(), null));
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
                final PopupContext popupContext = createPopupContext(getComponent(),null);
                try {
                    if (!permissionController.canModify( reservation, getUser()))
                    {
                        return;
                    }
                    editController.edit( reservation,popupContext );
                } catch (RaplaException ex) {
                    dialogUiFactory.showException (ex, popupContext);
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
