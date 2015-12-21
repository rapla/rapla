package org.rapla.plugin.tableview.internal;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.rapla.client.RaplaClientExtensionPoints;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.tablesorter.TableSorter;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.User;
import org.rapla.entities.configuration.Preferences;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.dynamictype.internal.DynamicTypeImpl;
import org.rapla.entities.dynamictype.internal.ParsedText;
import org.rapla.facade.CalendarModel;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaContextException;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.gui.MenuContext;
import org.rapla.gui.MenuFactory;
import org.rapla.gui.ObjectMenuFactory;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.ReservationController;
import org.rapla.gui.SwingCalendarView;
import org.rapla.gui.VisibleTimeInterval;
import org.rapla.gui.internal.action.AppointmentAction;
import org.rapla.gui.internal.common.InternMenus;
import org.rapla.gui.toolkit.MenuInterface;
import org.rapla.gui.toolkit.RaplaMenu;
import org.rapla.gui.toolkit.RaplaMenuItem;
import org.rapla.gui.toolkit.RaplaPopupMenu;
import org.rapla.plugin.abstractcalendar.IntervalChooserPanel;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.plugin.tableview.AppointmentTableColumn;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.TableViewExtensionPoints;
import org.rapla.plugin.tableview.internal.TableConfig.TableColumnConfig;

public class SwingAppointmentTableView extends RaplaGUIComponent implements SwingCalendarView, Printable, VisibleTimeInterval
{
    AppointmentTableModel appointmentTableModel;

    JTable table;
    CalendarModel model;
    IntervalChooserPanel dateChooser;
    JComponent scrollpane;
    TableSorter sorter;

	ActionListener copyListener = new CopyListener();
    CopyListener cutListener = new CopyListener();

	private JComponent container;

    public SwingAppointmentTableView( RaplaContext context, CalendarModel model, final boolean editable ) throws RaplaException
    {
        super( context );
        cutListener.setCut( true );
        table = new JTable() {
            private static final long serialVersionUID = 1L;
            
            public String getToolTipText(MouseEvent e) 
            {
                if (!editable)
                    return null;
                int rowIndex = rowAtPoint( e.getPoint() );
                AppointmentBlock app = appointmentTableModel.getAppointmentAt( sorter.modelIndex( rowIndex ));
                Reservation reservation = app.getAppointment().getReservation();
                return getInfoFactory().getToolTip( reservation );
            }
        };
    	scrollpane = new JScrollPane( table);
    	if ( editable )
        {
            scrollpane.setPreferredSize( new Dimension(600,800));
        	PopupTableHandler popupHandler = new PopupTableHandler();
        	scrollpane.addMouseListener( popupHandler);
        	table.addMouseListener( popupHandler );
        	container = new JPanel();
        	container.setLayout( new BorderLayout());
        	container.add( scrollpane, BorderLayout.CENTER);
        	JPanel extensionPanel = new JPanel();
        	extensionPanel.setLayout( new BoxLayout(extensionPanel, BoxLayout.X_AXIS));
        	container.add( extensionPanel, BorderLayout.SOUTH);
        	Collection< ? extends SummaryExtension> reservationSummaryExtensions = getContainer().lookupServicesFor(TableViewExtensionPoints.APPOINTMENT_TABLE_SUMMARY);
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
        User user = model.getUser();
        List<RaplaTableColumn<AppointmentBlock>> columnPlugins = TableConfig.loadColumns(getContainer(), "appointments", TableViewExtensionPoints.APPOINTMENT_TABLE_COLUMN,user);
       	
       	appointmentTableModel = new AppointmentTableModel( getLocale(),getI18n(), columnPlugins );
        sorter =  SwingReservationTableView.createAndSetSorter(model, table, TableViewPlugin.BLOCKS_SORTING_STRING_OPTION, appointmentTableModel);
        int column = 0;
        for (RaplaTableColumn<AppointmentBlock> col: columnPlugins)
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

       
        update(model);
        
        table.getSelectionModel().addListSelectionListener( new ListSelectionListener() {
    		
     	   public void valueChanged(ListSelectionEvent e) {
     		   updateEditMenu();
     	   }

 		
        });
    }

	protected void update(CalendarModel model) throws RaplaException 
	{
		List<AppointmentBlock> blocks = model.getBlocks();
		appointmentTableModel.setAppointments(blocks);
	}
   
    public void update() throws RaplaException
    {
    	update(model);
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

    
    List<AppointmentBlock> getSelectedEvents() {
        int[] rows = table.getSelectedRows();
        List<AppointmentBlock> selectedEvents = new ArrayList<AppointmentBlock>();
        for (int i=0;i<rows.length;i++)
        {
            AppointmentBlock reservation =appointmentTableModel.getAppointmentAt( sorter.modelIndex(rows[i]) );
            selectedEvents.add( reservation);
        }
        return selectedEvents;
    }
    
    protected void updateEditMenu() {
		List<AppointmentBlock> selectedEvents = getSelectedEvents();
		if ( selectedEvents.size() == 0 )
		{
			return;
		}
		RaplaMenu editMenu = getService(InternMenus.EDIT_MENU_ROLE);
		RaplaMenu newMenu = getService(InternMenus.NEW_MENU_ROLE);

		editMenu.removeAllBetween("EDIT_BEGIN", "EDIT_END");
		newMenu.removeAll();
		Point p  = null;
		try {
			updateMenu(editMenu,newMenu, p);
			newMenu.setEnabled(newMenu.getMenuComponentCount() > 0 && canUserAllocateSomething(getUser()));
			editMenu.setEnabled(canUserAllocateSomething(getUser()));
		} catch (RaplaException ex) {
			showException (ex,getComponent());
		}
	}

    private final class CopyListener implements ActionListener {
        boolean cut;

        public void actionPerformed(ActionEvent evt) 
		{
	        List<AppointmentBlock> selectedEvents = getSelectedEvents();
            if ( selectedEvents.size() == 1) {
            	AppointmentBlock appointmentBlock = selectedEvents.get( 0);
				try {
					Component sourceComponent = table;
					Point p = null;
					Collection<Allocatable> contextAllocatables = model.getMarkedAllocatables();
					ReservationController reservationController = getReservationController();
                    if ( isCut())
                    {
                        reservationController.cutAppointment(appointmentBlock,sourceComponent,p, contextAllocatables);
                    }
                    else
                    {
                        reservationController.copyAppointment(appointmentBlock,sourceComponent,p, contextAllocatables);
                    }
				} catch (RaplaException e) {
					showException( e, getComponent());
				}
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

    class PopupTableHandler extends MouseAdapter 
    {
        void showPopup(MouseEvent me) 
        {
            Point p = new Point(me.getX(), me.getY());
            RaplaPopupMenu menu= new RaplaPopupMenu();
            try {
                RaplaMenu newMenu = new RaplaMenu("EDIT_BEGIN");
                newMenu.setText(getString("new"));
                menu.add( newMenu );
                updateMenu( menu, newMenu, p);
                
                newMenu.setEnabled(newMenu.getMenuComponentCount() > 0 && canUserAllocateSomething(getUser()));
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
            List<AppointmentBlock> selectedEvents = getSelectedEvents();
            if (me.getClickCount() > 1  && selectedEvents.size() == 1 )
            {
                AppointmentBlock block = selectedEvents.get( 0);
                Appointment appointment = block.getAppointment();
                Reservation reservation = appointment.getReservation();
                if (!canModify( reservation ))
                {
                    return;
                }
                try {
                    getReservationController().edit( block);
                } catch (RaplaException ex) {
                    showException (ex,getComponent());
                }
            }
        }
    }
    
	protected void updateMenu(MenuInterface editMenu,MenuInterface newMenu,Point p)
			throws RaplaException, RaplaContextException {
		List<AppointmentBlock> selectedEvents = getSelectedEvents();
        AppointmentBlock focusedObject = null;
        if ( selectedEvents.size() == 1) 
        {
            focusedObject = selectedEvents.get( 0);
        }
      
        
        MenuContext menuContext = new MenuContext( getContext(), focusedObject,getComponent(), p);
        menuContext.put(RaplaCalendarViewListener.SELECTED_DATE, focusedObject != null ? new Date(focusedObject.getStart()): new Date());
        {
            menuContext.setSelectedObjects( selectedEvents);
        }
        
        // add the new reservations wizards
        {
            MenuFactory menuFactory = getService(MenuFactory.class);
            menuFactory.addReservationWizards( newMenu, menuContext, null);
        }
        //TODO add cut and copy for more then 1 block
        if ( selectedEvents.size() == 1) 	
		{
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
		}
        if ( selectedEvents.size() != 0)    
        {    
            addObjectMenu(editMenu, menuContext, "EDIT_BEGIN");
        }
       
	}
   

    Printable printable = null;
    /**
     * @see java.awt.print.Printable#print(java.awt.Graphics, java.awt.print.PageFormat, int)
     */
    public int print(Graphics graphics, PageFormat format, int page) throws PrinterException {
    	MessageFormat f1 = new MessageFormat( model.getNonEmptyTitle());
        MessageFormat f2 = new MessageFormat("- {0} -");
    	Printable  printable = table.getPrintable( JTable.PrintMode.FIT_WIDTH,f1, f2 );
        return printable.print( graphics, format, page);
    }

    
    private MenuInterface addObjectMenu( MenuInterface menu, MenuContext context, String afterId ) throws RaplaException
    {
    	Component parent = getComponent();
    	AppointmentBlock appointmentBlock = (AppointmentBlock) context.getFocusedObject();
    	Point p = context.getPoint();
    	@SuppressWarnings("unchecked")
        Collection<AppointmentBlock> selection = (Collection<AppointmentBlock>)context.getSelectedObjects();
    	if ( appointmentBlock != null)
	    {
			{
				AppointmentAction action = new AppointmentAction(getContext(),parent,p);
				action.setDelete(appointmentBlock);
				menu.insertAfterId(new JMenuItem(action), afterId);
			}
			{
				AppointmentAction action = new AppointmentAction(getContext(),parent,p);
				action.setView(appointmentBlock);
				menu.insertAfterId(new JMenuItem(action), afterId);
			}
			
			{
		        AppointmentAction action = new AppointmentAction(getContext(),parent,p);
		        action.setEdit(appointmentBlock);
		        menu.insertAfterId(new JMenuItem(action), afterId);
	        }
	        
	    }
    	else if ( selection !=  null && selection.size() > 0)
    	{
	        AppointmentAction action = new AppointmentAction(getContext(),parent,p);
	        action.setDeleteSelection(selection);
	        menu.insertAfterId(new JMenuItem(action), afterId);
        }
	
	    
		Iterator<?> it = getContainer().lookupServicesFor( RaplaClientExtensionPoints.OBJECT_MENU_EXTENSION).iterator();
		while (it.hasNext())
		{
			ObjectMenuFactory objectMenuFact = (ObjectMenuFactory) it.next();
			Appointment appointment = appointmentBlock != null ? appointmentBlock.getAppointment(): null;
			RaplaMenuItem[] items = objectMenuFact.create( context,  appointment);
			for ( int i =0;i<items.length;i++)
			{
			    RaplaMenuItem item =  items[i];
			    menu.insertAfterId( item, afterId);
			}
		}
		return  menu;
    }

	public TimeInterval getVisibleTimeInterval() {
		return new TimeInterval(model.getStartDate(), model.getEndDate());
	}
 }
