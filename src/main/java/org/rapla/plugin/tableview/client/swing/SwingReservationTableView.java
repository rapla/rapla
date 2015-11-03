package org.rapla.plugin.tableview.client.swing;

import org.rapla.RaplaResources;
import org.rapla.client.ReservationController;
import org.rapla.client.swing.*;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.tablesorter.TableSorter;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.common.InternMenus;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.MenuInterface;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.inject.Extension;
import org.rapla.plugin.abstractcalendar.client.swing.IntervalChooserPanel;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.client.swing.extensionpoints.ReservationSummaryExtension;
import org.rapla.plugin.tableview.client.swing.extensionpoints.SummaryExtension;
import org.rapla.plugin.tableview.internal.TableConfig;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.event.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

@Extension(id="ReservationTableView", provides=SwingCalendarView.class)
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

    private final ReservationController reservationController;

    private final RaplaImages raplaImages;
    
    @Inject
    public SwingReservationTableView(RaplaContext context, final CalendarModel model, final Set<ReservationSummaryExtension> reservationSummaryExtensions,
            final boolean editable, TableConfig.TableConfigLoader tableConfigLoader, MenuFactory menuFactory, ReservationController reservationController,
            final InfoFactory<Component, DialogUI> infoFactory, RaplaImages raplaImages, IntervalChooserPanel dateChooser) throws RaplaException
    {
        super( context );
        this.tableConfigLoader = tableConfigLoader;
        this.menuFactory = menuFactory;
        this.reservationController = reservationController;
        this.raplaImages = raplaImages;
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

        List<RaplaTableColumn<Reservation,TableColumn>> reservationColumnConfigured = tableConfigLoader.loadColumns("events");
        final RaplaResources i18n = getI18n();
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
        MenuContext menuContext = new MenuContext( getContext(), focusedObject, new SwingPopupContext(getComponent(),p));
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
                    reservationController.edit( reservation );
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
