/*--------------------------------------------------------------------------*
 | Copyright (C) 2006  Christopher Kohlhaas                                 |
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

package org.rapla.plugin.abstractcalendar.client.swing;

import org.jetbrains.annotations.NotNull;
import org.rapla.RaplaResources;
import org.rapla.client.EditController;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.dialog.InfoFactory;
import org.rapla.client.extensionpoints.ObjectMenuFactory;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.menu.MenuFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.SwingCalendarView;
import org.rapla.client.swing.VisibleTimeInterval;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendarview.BuildStrategy;
import org.rapla.components.calendarview.CalendarView;
import org.rapla.components.calendarview.swing.AbstractSwingCalendar;
import org.rapla.components.calendarview.swing.ViewListener;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarModel;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.rapla.plugin.abstractcalendar.DateChooserPanel;
import org.rapla.plugin.abstractcalendar.GroupAllocatablesStrategy;
import org.rapla.plugin.abstractcalendar.MultiCalendarPrint;
import org.rapla.plugin.abstractcalendar.RaplaBuilder;
import org.rapla.plugin.abstractcalendar.RaplaCalendarViewListener;
import org.rapla.scheduler.Observable;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;
import org.rapla.scheduler.sync.SynchronizedCompletablePromise;

import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.LocalDateTime;
public abstract class AbstractRaplaSwingCalendar extends RaplaGUIComponent
        implements SwingCalendarView, DateChangeListener, MultiCalendarPrint, VisibleTimeInterval, Printable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractRaplaSwingCalendar.class);
    protected final CalendarModel model;
    protected final AbstractSwingCalendar view;
    protected DateChooserPanel dateChooser;
    JComponent container;
    JLabel titleView;
    int units = 1;
    protected final Set<ObjectMenuFactory> objectMenuFactories;
    protected final MenuFactory menuFactory;
    protected final Supplier<DateRenderer> dateRendererProvider;
    protected final CalendarSelectionModel calendarSelectionModel;
    protected final RaplaClipboard clipboard;
    protected final ReservationController reservationController;
    protected final InfoFactory infoFactory;
    protected final DialogUiFactoryInterface dialogUiFactory;
    protected final AppointmentFormater appointmentFormater;
    protected final EditController editController;
    private final boolean printing;

    public AbstractRaplaSwingCalendar(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, CalendarModel model, boolean editable,
            boolean printing, final Set<ObjectMenuFactory> objectMenuFactories, MenuFactory menuFactory, Supplier<DateRenderer> dateRendererProvider,
            CalendarSelectionModel calendarSelectionModel, RaplaClipboard clipboard, ReservationController reservationController, InfoFactory infoFactory,
            DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface,
            AppointmentFormater appointmentFormater, EditController editController) throws RaplaException
    {
        super(facade, i18n, raplaLocale);
        this.model = model;
        this.printing = printing;
        this.objectMenuFactories = objectMenuFactories;
        this.menuFactory = menuFactory;
        this.dateRendererProvider = dateRendererProvider;
        this.calendarSelectionModel = calendarSelectionModel;
        this.clipboard = clipboard;
        this.reservationController = reservationController;
        this.infoFactory = infoFactory;
        this.dialogUiFactory = dialogUiFactory;
        this.appointmentFormater = appointmentFormater;
        this.editController = editController;

        boolean printable = isPrintContext();
        view = createView(!printable);
        view.setEditable(editable);
        view.setLocale(getRaplaLocale());
        if (editable)
        {
            view.addCalendarViewListener(createListener());
        }

        if (!printable)
        {
            container = view.getComponent();
        }
        else
        {
            container = new JPanel();
            container.setLayout(new BorderLayout());
            container.setOpaque(false);
            view.getComponent().setOpaque(false);
            titleView = new JLabel();
            titleView.setFont(new Font("SansSerif", Font.BOLD, 14));
            titleView.setOpaque(false);
            titleView.setForeground(Color.black);
            //titleView.setHorizontalAlignment(JLabel.CENTER);
            titleView.setBorder(BorderFactory.createEmptyBorder(0, 11, 12, 11));

            container.add(titleView, BorderLayout.NORTH);
            container.add(view.getComponent(), BorderLayout.CENTER);
        }

        dateChooser = new DateChooserPanel(facade, i18n, raplaLocale, model, dateRenderer, ioInterface);
        dateChooser.addDateChangeListener(this);
        dateChooser.setIncrementSize(getIncrementSize());
    }

    protected boolean isPrintContext()
    {
        return printing;
    }

    abstract protected AbstractSwingCalendar createView(boolean showScrollPane) throws RaplaException;

    abstract protected void configureView() throws RaplaException;

    abstract public DateTools.IncrementSize getIncrementSize();

    /**
     * @throws RaplaException
     */
    protected ViewListener createListener() throws RaplaException
    {
        return new RaplaCalendarViewListener(getClientFacade(), getI18n(), getRaplaLocale(), model, view.getComponent(),
                menuFactory,  reservationController,  dialogUiFactory, editController);
    }

    public JComponent getDateSelection()
    {
        return dateChooser.getComponent();
    }

    @Override
    public void dateChanged(DateChangeEvent evt)
    {
        triggerUpdate();
    }

    public Observable triggerUpdate()
    {
        Promise<Void> result = initializeBuilder()
                .execOn(SwingUtilities::invokeLater)
                .thenAccept(this::update)
                .exceptionally(this::handleException);
        return org.rapla.scheduler.Observables.toObservable( result, getFacade().getScheduler().getExecutor());
    }

    public void handleException(Throwable ex)
    {
        PopupContext popupContext = dialogUiFactory.createPopupContext(()->view.getComponent());
        dialogUiFactory.showException(ex, popupContext);
    }

    public void update(RaplaBuilder builder) throws RaplaException
    {

        if (titleView != null)
        {
            titleView.setText(model.getNonEmptyTitle());
        }
        dateChooser.update();
        if (!isPrintContext())
        {
            int minBlockWidth = getCalendarOptions().getMinBlockWidth();
            view.setMinBlockWidth(minBlockWidth);
        }
        view.rebuild(builder);
        if (!view.isEditable())
        {
            Dimension size = view.getComponent().getPreferredSize();
            container.setBounds(0, 0, size.width, size.height + 40);
        }
    }

    private Promise<RaplaBuilder> initializeBuilder()
    {
        try {
            configureView();
        } catch (RaplaException e) {
            return new ResolvedPromise<>(e);
        }
        LocalDateTime startDate = getStartDate();
        LocalDateTime endDate = getEndDate();
        ensureViewTimeframeIsInModel(startDate, endDate);
        final Promise<RaplaBuilder> builderPromise = createBuilder();
        return builderPromise;
    }

    protected LocalDateTime getEndDate()
    {
        return view.getEndDate();
    }

    protected LocalDateTime getStartDate()
    {
        return view.getStartDate();
    }

    public TimeInterval getVisibleTimeInterval()
    {
        return new TimeInterval(getStartDate(), getEndDate());
    }

    protected void ensureViewTimeframeIsInModel(LocalDateTime startDate, LocalDateTime endDate)
    {
        //      Update start- and enddate of the model
        LocalDateTime modelStart = model.getStartDate();
        LocalDateTime modelEnd = model.getEndDate();
        if (modelStart == null || modelStart.isAfter(startDate))
        {
            model.setStartDate(startDate);
        }
        if (modelEnd == null || modelEnd.isBefore(endDate))
        {
            model.setEndDate(endDate);
        }
    }

    protected Promise<RaplaBuilder> createBuilder()
    {
        RaplaBuilder builder = new SwingRaplaBuilder(getFacade(), getI18n(), getRaplaLocale(), appointmentFormater);
        LocalDateTime startDate = getStartDate();
        LocalDateTime endDate = getEndDate();
        final Promise<RaplaBuilder> builderPromise = builder.initFromModel(model, startDate, endDate);
        final Promise<RaplaBuilder> nextBuilderPromise = builderPromise.thenApply((initializedBuilder) ->
        {
            initializedBuilder.setRepeatingVisible(view.isEditable());
            BuildStrategy strategy = createStrategy(initializedBuilder);
            initializedBuilder.setBuildStrategy(strategy);
            return initializedBuilder;
        });
        return nextBuilderPromise;
    }

    @NotNull
    protected BuildStrategy createStrategy(RaplaBuilder initializedBuilder) throws  RaplaException
    {
        GroupAllocatablesStrategy strategy = new GroupAllocatablesStrategy(getRaplaLocale().getLocale());
        boolean compactColumns = getCalendarOptions().isCompactColumns() || initializedBuilder.getAllocatables().size() == 0;
        strategy.setFixedSlotsEnabled(!compactColumns);
        strategy.setResolveConflictsEnabled(true);
        strategy.setOffsetMinutes( view.getOffsetMinutes());
        return strategy;
    }

    public JComponent getComponent()
    {
        return container;
    }

    public List<Allocatable> getSortedAllocatables() throws RaplaException
    {
        List<Allocatable> sortedAllocatables = model.getSelectedAllocatablesSorted();
        return sortedAllocatables;
    }

    public void scrollToStart()
    {
    }

    public CalendarView getCalendarView()
    {
        return view;
    }

    //DateTools.addDays(LocalDateTime.of(), 100);
    LocalDateTime currentPrintDate;
    Map<LocalDateTime, Integer> pageStartMap = new HashMap<>();
    Double scaleFactor = null;

    /**
     * @see java.awt.print.Printable#print(java.awt.Graphics, java.awt.print.PageFormat, int)
     */
    @Override
    public int print(Graphics g, PageFormat format, int page) throws PrinterException
    {

    	/*JFrame frame = new JFrame();
        frame.setSize(300,300);
        frame.getContentPane().add( container);
        frame.pack();
        frame.setVisible(false);*/
        final LocalDateTime startDate = model.getStartDate();
        final LocalDateTime endDate = model.getEndDate();
        final LocalDateTime selectedDate = model.getSelectedDate();

        int pages = getUnits();
        LocalDateTime targetDate = DateTools.add(selectedDate, getIncrementSize(), pages - 1);

        if (page <= 0)
        {
            currentPrintDate = selectedDate;
            pageStartMap.clear();
            scaleFactor = null;
            pageStartMap.put(currentPrintDate, page);
        }
        while (true)
        {
            int printOffset = (int) DateTools.countDays(selectedDate, currentPrintDate);
            model.setStartDate(DateTools.addDays(startDate, printOffset));
            model.setEndDate(DateTools.addDays(endDate, printOffset));
            model.setSelectedDate(DateTools.addDays(selectedDate, printOffset));
            try
            {
                Graphics2D g2 = (Graphics2D) g;
                try
                {
                    // get all reservations
                    final Promise<RaplaBuilder> builderPromise = initializeBuilder();
                    RaplaBuilder builder = SynchronizedCompletablePromise.waitFor(builderPromise, 5000);
                    update(builder);
                }
                catch (Exception e)
                {
                    LOGGER.error(e.getMessage(), e);
                    throw new PrinterException(e.getMessage());
                }

                double preferedHeight = view.getComponent().getPreferredSize().getHeight();
                if (scaleFactor == null)
                {
                    scaleFactor = Math.min(1, 1 / Math.min(2.5, preferedHeight / format.getImageableHeight()));
                }
                double newWidth = format.getImageableWidth() / scaleFactor;
                double scaledPreferedHeigth = preferedHeight * scaleFactor;

                Component component = container;
                view.updateSize((int) newWidth);
                container.setBounds(0, 0, (int) newWidth, (int) preferedHeight);
                try
                {
                    final Promise<RaplaBuilder> builderPromise = initializeBuilder();
                    RaplaBuilder builder = SynchronizedCompletablePromise.waitFor(builderPromise, 5000);
                    update(builder);
                }
                catch (Exception e)
                {
                    LOGGER.error(e.getMessage(), e);
                    throw new PrinterException(e.getMessage());
                }

                Integer pageStart = pageStartMap.get(currentPrintDate);
                if (pageStart == null)
                {
                    return NO_SUCH_PAGE;
                }
                int translatey = (int) ((page - pageStart) * format.getImageableHeight());
                if (translatey > scaledPreferedHeigth - 20)
                {
                    if (targetDate != null && currentPrintDate.isBefore(targetDate))
                    {
                        currentPrintDate = DateTools.add(currentPrintDate, getIncrementSize(), 1);
                        pageStartMap.put(currentPrintDate, page);
                        continue;
                    }
                    else
                    {
                        return NO_SUCH_PAGE;
                    }
                }
                if (translatey < 0 && targetDate != null)
                {
                    currentPrintDate = DateTools.add(currentPrintDate, getIncrementSize(), -1);
                    continue;
                }
                if (targetDate != null && currentPrintDate.isAfter(targetDate))
                {
                    return NO_SUCH_PAGE;
                }

                g2.translate(format.getImageableX(), format.getImageableY() - translatey);
                g2.clipRect(0, translatey, (int) (format.getImageableWidth()), (int) (format.getImageableHeight()));
                g2.scale(scaleFactor, scaleFactor);

                RepaintManager rm = RepaintManager.currentManager(component);
                boolean db = rm.isDoubleBufferingEnabled();
                try
                {
                    rm.setDoubleBufferingEnabled(false);
                    component.printAll(g);
                    return Printable.PAGE_EXISTS;
                }
                finally
                {
                    rm.setDoubleBufferingEnabled(db);
                }
            }
            finally
            {
                model.setStartDate(startDate);
                model.setEndDate(endDate);
                model.setSelectedDate(selectedDate);
            }
        }
    }

    public String getCalendarUnit()
    {
        DateTools.IncrementSize incrementSize = getIncrementSize();
        final RaplaResources i18n = this.i18n;
        return MultiCalendarPrint.getIncrementName(incrementSize, i18n);
    }

    public int getUnits()
    {
        return units;
    }

    public void setUnits(int units)
    {
        this.units = units;
    }

}
