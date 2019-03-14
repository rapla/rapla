package org.rapla.plugin.tableview.client.swing;

import io.reactivex.functions.Consumer;
import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.menu.MenuInterface;
import org.rapla.client.menu.SelectionMenuContext;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.VisibleTimeInterval;
import org.rapla.client.swing.internal.RaplaMenuBarContainer;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.toolkit.DisabledGlassPane;
import org.rapla.client.swing.toolkit.RaplaMenu;
import org.rapla.client.swing.toolkit.RaplaPopupMenu;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.tablesorter.TableSorter;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.Entity;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.AppointmentBlock;
import org.rapla.entities.domain.Reservation;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.plugin.abstractcalendar.client.swing.IntervalChooserPanel;
import org.rapla.plugin.tableview.RaplaTableColumn;
import org.rapla.plugin.tableview.RaplaTableModel;
import org.rapla.plugin.tableview.TableViewPlugin;
import org.rapla.plugin.tableview.client.swing.extensionpoints.SummaryExtension;
import org.rapla.plugin.tableview.internal.TableConfig;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.storage.PermissionController;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
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
import java.util.function.Supplier;

public class SwingTableView<T> extends RaplaGUIComponent implements SwingCalendarView, Printable, VisibleTimeInterval
{

    RaplaTableModel<T, TableColumn> tableModel;
    RaplaSwingTableModel swingTableModel;

    JTable table;
    CalendarModel model;
    IntervalChooserPanel dateChooser;
    JScrollPane scrollpane;
    JComponent container;
    TableSorter sorter;

    CopyListener copyListener = new CopyListener();
    CopyListener cutListener = new CopyListener();
    DisabledGlassPane glassPane;
    private final MenuFactory menuFactory;

    private final EditController editController;
    private final ReservationController reservationController;

    private final DialogUiFactoryInterface dialogUiFactory;

    private final PermissionController permissionController;

    private final IOInterface ioInterface;

    private final RaplaMenuBarContainer menuBar;

    private final Supplier<Promise<List<T>>> initFunction;

    public SwingTableView(RaplaMenuBarContainer menuBar, ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger,
                                     final CalendarModel model, final Set<? extends SummaryExtension> summaryExtensions, final boolean editable, boolean printing,
                          List<RaplaTableColumn<T,TableColumn>> raplaTableColumns, MenuFactory menuFactory, EditController editController, ReservationController reservationController,
                                     final InfoFactory infoFactory, IntervalChooserPanel dateChooser, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface
            ,Supplier<Promise<List<T>>> initFunction
            ,String tableName
                ) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.initFunction = initFunction;
        this.i18n = i18n;
        this.menuBar = menuBar;
        this.menuFactory = menuFactory;
        this.editController = editController;
        this.reservationController = reservationController;
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
                final int sortedRowIndex = sorter.modelIndex(rowIndex);
                T rowObject = tableModel.getObjectAt(sortedRowIndex);
                return infoFactory.getToolTip( rowObject );
            }
        };

        scrollpane = new JScrollPane( table);
        if ( editable )
        {
            container = new JLayeredPane();
            JPanel cont = new JPanel();
            ComponentListener l = new ComponentAdapter()
            {
                @Override
                public void componentResized(ComponentEvent e)
                {
                    final Dimension size = e.getComponent().getSize();
                    cont.setSize( size);
                    glassPane.setSize( size);
                }
            };
            container.addComponentListener( l);
        	cont.setLayout( new BorderLayout());
            glassPane = new DisabledGlassPane();
            glassPane.deactivate();
            cont.add( scrollpane, BorderLayout.CENTER);
            JPanel extensionPanel = new JPanel();
        	extensionPanel.setLayout( new BoxLayout(extensionPanel, BoxLayout.X_AXIS));
        	cont.add( extensionPanel, BorderLayout.SOUTH);
        	PopupTableHandler popupHandler = new PopupTableHandler();
            final Dimension preferredSize = new Dimension(600, 800);
            scrollpane.setPreferredSize(preferredSize);
            scrollpane.addMouseListener( popupHandler);
        	table.addMouseListener( popupHandler );
     		for ( SummaryExtension summary:summaryExtensions)
     		{
     			summary.init(table, extensionPanel);
     		}
            cont.setPreferredSize( preferredSize);
     		cont.setSize( preferredSize);
            container.setSize( preferredSize);
            glassPane.setSize( preferredSize);
            container.add( cont, JLayeredPane.DEFAULT_LAYER);
            container.add( glassPane, JLayeredPane.PALETTE_LAYER);
        }
        else
        {
            Dimension size = table.getPreferredSize();
            scrollpane.setBounds( 0,0,600, (int)size.getHeight());
            container = scrollpane;
        }
        this.model = model;
        tableModel = new RaplaTableModel<>(raplaTableColumns);
        swingTableModel= new RaplaSwingTableModel(tableModel);
        String sortingStringOption = TableViewPlugin.getSortingStringOption( tableName);
        sorter = createAndSetSorter(model, table, sortingStringOption, swingTableModel);
        int column = 0;
        for (RaplaTableColumn<T,TableColumn> col: raplaTableColumns)
        {
            final TableColumnModel columnModel = table.getColumnModel();
            final TableColumn column1 = columnModel.getColumn(column);
            col.init(column1);
        	column++;
        }
        table.setColumnSelectionAllowed( true );
        table.setRowSelectionAllowed( true);
        table.getTableHeader().setReorderingAllowed(false);
    	table.registerKeyboardAction(copyListener,getString("copy"),COPY_STROKE,JComponent.WHEN_FOCUSED);
    	table.registerKeyboardAction(cutListener,getString("cut"),CUT_STROKE,JComponent.WHEN_FOCUSED);
        this.dateChooser = dateChooser;
        dateChooser.addDateChangeListener(evt -> triggerUpdate());
    }

    void handleException(Promise<Void> promise)
    {
        PopupContext popupContext = dialogUiFactory.createPopupContext( ()->getComponent());
        promise.exceptionally(ex->dialogUiFactory.showException(ex, popupContext));
    }


    private final class CopyListener implements ActionListener, Consumer<PopupContext> {
        boolean cut;

        @Override
        public void accept(PopupContext context) {
            actionPerformed( new ActionEvent(table,ActionEvent.ACTION_PERFORMED,"copy"));
        }

        public void actionPerformed(ActionEvent evt)
		{
	        List<T> selectedEvents = getSelectedEvents();
	        Collection<Allocatable> markedAllocatables = model.getMarkedAllocatables();
	        final Promise<Void> ready;
            final boolean isCut = isCut();
            Point p = null;
            PopupContext popupContext = createPopupContext(table, p);
            ready = copyCut(selectedEvents, markedAllocatables, isCut, popupContext);
            handleException( ready.thenRun(()->copy(table, evt, ioInterface, getRaplaLocale())));
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
        sorter.addTableModelListener(e -> {
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
        });
        table.setModel(  sorter );
        return sorter;
    }

    protected void updateEditMenu() {
		List<T> selectedEvents = getSelectedEvents();
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
            boolean canUserAllocateSomething = permissionController.canUserAllocateSomething(user);
			boolean enableNewMenu = newMenu.getMenuComponentCount() > 0 && canUserAllocateSomething;
			newMenu.setEnabled(enableNewMenu);
			editMenu.setEnabled(permissionController.canUserAllocateSomething(user));
		} catch (RaplaException ex) {
		    dialogUiFactory.showException (ex,new SwingPopupContext(getComponent(), null));
		}
	}

    Listener listener;

    public Observable triggerUpdate()
    {
        glassPane.activate(i18n.getString("load"));
        final Promise<List<T>> promise = initFunction.get();
        final Promise<Void> result = promise.thenAccept((reservations) ->
        {
            tableModel.setObjects(new ArrayList<>(reservations));
        }).execOn(SwingUtilities::invokeLater).thenRun(  ()->  {
            if ( listener == null)
            {
                listener = new Listener();
                table.getSelectionModel().addListSelectionListener(listener);
                table.addFocusListener(listener);
            }
            table.getSelectionModel().addListSelectionListener(listener);
            table.addFocusListener(listener);
            swingTableModel.fireTableDataChanged();
            dateChooser.update();});
        result.finally_(()->
                glassPane.deactivate()
        );
        return getFacade().getScheduler().toObservable(result);
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
        SelectionMenuContext menuContext = createMenuContext(p);
        String afterId = "EDIT_BEGIN";
        menuFactory.addCopyCutListMenu(  editMenu, menuContext, afterId, copyListener, cutListener);
        menuFactory.addObjectMenu( editMenu, menuContext,afterId);
        // add the new reservations wizards
        menuFactory.addReservationWizards( newMenu, menuContext, afterId);
	}

    @NotNull
    private SelectionMenuContext createMenuContext(Point p)
    {
        List<T> selectedEvents = getSelectedEvents();
        T focusedObject = null;
        if ( selectedEvents.size() == 1) {
            focusedObject = selectedEvents.get( 0);
        }
        final SwingPopupContext popupContext = new SwingPopupContext(getComponent(), p);
        SelectionMenuContext menuContext = new SelectionMenuContext(  focusedObject, popupContext);
        menuContext.setSelectedObjects( selectedEvents);
        return menuContext;
    }

    List<T> getSelectedEvents() {
        int[] rows = table.getSelectedRows();
        List<T> selectedEvents = new ArrayList<>();
        for (int i=0;i<rows.length;i++)
        {
            T reservation = tableModel.getObjectAt( sorter.modelIndex(rows[i]) );
            selectedEvents.add( reservation);
        }
        return selectedEvents;
    }

    class PopupTableHandler extends MouseAdapter {

        void showPopup(MouseEvent me) {
        	 try
             {
                 Point p = new Point(me.getX(), me.getY());
                 PopupContext popupContext = new SwingPopupContext((Component) me.getSource(), p);
                 RaplaPopupMenu menu= new RaplaPopupMenu(popupContext);
                 SelectionMenuContext context = createMenuContext( p);
                 menuFactory.addEventMenu(menu, context , copyListener, cutListener);
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
            List<T> selectedEvents = getSelectedEvents();
            if (me.getClickCount() > 1  && selectedEvents.size() == 1 )
            {
                T reservation = selectedEvents.get( 0);
                final PopupContext popupContext = createPopupContext(getComponent(),null);
                try {
                    edit(reservation, popupContext);
                } catch (RaplaException ex) {
                    dialogUiFactory.showException (ex, popupContext);
                }
            }
        }
    }



    Printable printable = null;
    /**
     * @see Printable#print(Graphics, PageFormat, int)
     */
    public int print(Graphics graphics, PageFormat format, int page) throws PrinterException {
    	MessageFormat f1 = new MessageFormat( model.getNonEmptyTitle());
    	Printable  printable = table.getPrintable( JTable.PrintMode.FIT_WIDTH,f1, null );
        return printable.print( graphics, format, page);
    }

	public TimeInterval getVisibleTimeInterval() {
		return new TimeInterval(model.getStartDate(), model.getEndDate());
	}

    private void edit(T object, PopupContext popupContext) throws RaplaException
    {
        if ( object instanceof Entity)
        {
            Entity reservation = (Entity ) object;
            if (!permissionController.canModify(reservation, getUser()))
            {
                return;
            }
            editController.edit(reservation, popupContext);
        }
        else if ( object instanceof AppointmentBlock)
        {
            AppointmentBlock block = (AppointmentBlock ) object;
            Appointment appointment = block.getAppointment();
            Reservation reservation = appointment.getReservation();
            if (!permissionController.canModify(reservation, getUser()))
            {
                return;
            }
            editController.edit(reservation, popupContext);
        }
    }

    private Promise<Void> copyCut(List<T> selectedObjects, Collection<Allocatable> markedAllocatables, boolean isCut, PopupContext popupContext)
    {
        if ( selectedObjects.isEmpty())
        {
            return  ResolvedPromise.VOID_PROMISE;
        }
        Promise<Void> ready;
        final T first = selectedObjects.get(0);
        if ( first instanceof AppointmentBlock)
        {
            if (selectedObjects.size() == 1)
            {
                AppointmentBlock appointmentBlock = (AppointmentBlock) first;
                Collection<Allocatable> contextAllocatables = model.getMarkedAllocatables();
                if (isCut)
                {
                    ready = reservationController.cutAppointment(appointmentBlock, popupContext, contextAllocatables);
                }
                else
                {
                    ready = reservationController.copyAppointmentBlock(appointmentBlock, popupContext, contextAllocatables);
                }

            }
            else
            {
                ready=ResolvedPromise.VOID_PROMISE;
            }
        }
        else if (first instanceof  Reservation)
        {
            Collection<Reservation> selectedEvents = (Collection<Reservation>) selectedObjects;
            if (isCut)
            {
                ready = reservationController.cutReservations(selectedEvents, markedAllocatables);
            }
            else
            {
                ready = reservationController.copyReservations(selectedEvents, markedAllocatables);
            }
        }
        else
        {
            ready=ResolvedPromise.VOID_PROMISE;
        }
        return ready;
    }


}
