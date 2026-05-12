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
package org.rapla.client.swing.internal.edit.reservation;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.RaplaWidget;
import org.rapla.client.edit.reservation.RepeatingRuleModel;
import org.rapla.client.edit.reservation.RepeatingRuleProjector;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.DayChooserState;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndDateBinding;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingMode;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.EndingPanelVisibility;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.ExceptionButtonState;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.ExceptionCountStyle;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.RepeatingPanelVisibility;
import org.rapla.client.edit.reservation.RepeatingRuleProjector.WeekdaySelection;
import org.rapla.client.edit.reservation.RepeatingRuleWriter;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.AppointmentEditExtensionFactory;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.images.RaplaImages;
import org.rapla.client.swing.internal.SwingPopupContext;
import org.rapla.client.swing.internal.common.PeriodChooser;
import org.rapla.client.swing.toolkit.MonthChooser;
import org.rapla.client.swing.toolkit.RaplaButton;
import org.rapla.client.swing.toolkit.WeekdayChooser;
import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.DateRenderer;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.calendar.RaplaTime;
import org.rapla.components.calendarview.WeekdayMapper;
import org.rapla.components.iolayer.IOInterface;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
import org.rapla.components.util.TimeInterval;
import org.rapla.components.util.undo.CommandHistory;
import org.rapla.components.util.undo.CommandUndo;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Repeating;
import org.rapla.entities.domain.RepeatingEnding;
import org.rapla.entities.domain.RepeatingType;
import org.rapla.entities.domain.Reservation;
import org.rapla.entities.domain.ReservationHelper;
import org.rapla.entities.domain.internal.AppointmentImpl;
import org.rapla.facade.CalendarOptions;
import org.rapla.facade.PeriodModel;
import org.rapla.facade.client.ClientFacade;
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.logger.Logger;
import org.rapla.scheduler.Promise;
import org.rapla.scheduler.ResolvedPromise;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import java.time.LocalDateTime;
/** GUI for editing a single Appointment. */
public class AppointmentController extends RaplaGUIComponent implements Disposable, RaplaWidget, AppointmentEditExtensionFactory.AppointmentEditExtensionEvents
{
    JPanel panel = new JPanel();

    SingleEditor singleEditor = new SingleEditor();

    JPanel repeatingContainer = new JPanel();
    RepeatingEditor repeatingEditor = new RepeatingEditor();

    Appointment appointment = null;
    Repeating repeating;

    RepeatingType savedRepeatingType = null;

    ArrayList<ChangeListener> listenerList = new ArrayList<>();
    JPanel repeatingType = new JPanel();
    JRadioButton noRepeating = new JRadioButton();
    JRadioButton weeklyRepeating = new JRadioButton();
    JRadioButton dailyRepeating = new JRadioButton();
    JRadioButton monthlyRepeating = new JRadioButton();
    JRadioButton yearlyRepeating = new JRadioButton();

    CardLayout repeatingCard = new CardLayout();
    // Button for splitting appointments
    RaplaButton convertButton = new RaplaButton();

    private final CommandHistory commandHistory;

    LocalDateTime selectedEditDate = null;

    private final DateRenderer dateRenderer;

    private final DialogUiFactoryInterface dialogUiFactory;

    private final IOInterface ioInterface;
    private TimeInterval lastModifiedExceptionDialogInterval = null;

    public AppointmentController(ClientFacade facade, RaplaResources i18n, RaplaLocale raplaLocale, Logger logger, CommandHistory commandHistory,
            DateRenderer dateRenderer, DialogUiFactoryInterface dialogUiFactory, IOInterface ioInterface, Set<AppointmentEditExtensionFactory> appointmentEditFactory) throws RaplaException
    {
        super(facade, i18n, raplaLocale, logger);
        this.commandHistory = commandHistory;
        this.dateRenderer = dateRenderer;
        this.dialogUiFactory = dialogUiFactory;
        this.ioInterface = ioInterface;
        panel.setLayout(new BorderLayout());
        panel.add(repeatingType, BorderLayout.NORTH);
        repeatingType.setLayout(new BoxLayout(repeatingType, BoxLayout.X_AXIS));
        repeatingType.add(noRepeating);
        repeatingType.add(weeklyRepeating);
        repeatingType.add(dailyRepeating);
        repeatingType.add(monthlyRepeating);
        repeatingType.add(yearlyRepeating);

        repeatingType.add(Box.createHorizontalStrut(40));
        repeatingType.add(convertButton);
        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(noRepeating);
        buttonGroup.add(weeklyRepeating);
        buttonGroup.add(dailyRepeating);
        buttonGroup.add(monthlyRepeating);
        buttonGroup.add(yearlyRepeating);

        if ( appointmentEditFactory.size() > 0) {
            JPanel editExtensionPanel = new JPanel();
            editExtensionPanel.setLayout(new BoxLayout(editExtensionPanel, BoxLayout.X_AXIS));
            for ( AppointmentEditExtensionFactory factory:appointmentEditFactory) {
                RaplaWidget widget = factory.createField( this);
                if ( widget != null) {
                    editExtensionPanel.add((JComponent)widget.getComponent());
                }
            }
            panel.add(editExtensionPanel, BorderLayout.SOUTH);
        }

        panel.add(repeatingContainer, BorderLayout.CENTER);


        Border emptyLineBorder = new Border()
        {
            final Insets insets = new Insets(1, 0, 0, 0);
            final Color COLOR = Color.LIGHT_GRAY;

            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
            {
                g.setColor(COLOR);
                g.drawLine(0, 0, c.getWidth(), 0);

            }

            public Insets getBorderInsets(Component c)
            {
                return insets;
            }

            public boolean isBorderOpaque()
            {
                return true;
            }

        };

        Border outerBorder = (BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5)
                // ,BorderFactory.createEmptyBorder()
                , emptyLineBorder));

        repeatingContainer.setBorder(BorderFactory.createCompoundBorder(outerBorder, BorderFactory.createEmptyBorder(10, 5, 10, 5)));
        repeatingContainer.setLayout(repeatingCard);
        repeatingContainer.add(singleEditor.getComponent(), "0");
        repeatingContainer.add(repeatingEditor.getComponent(), "1");

        singleEditor.initialize();
        repeatingEditor.initialize();
        ActionListener listener = evt -> switchRepeatings();
        noRepeating.addActionListener(listener);
        weeklyRepeating.addActionListener(listener);
        monthlyRepeating.addActionListener(listener);
        dailyRepeating.addActionListener(listener);
        yearlyRepeating.addActionListener(listener);
        noRepeating.setText(getString("no_repeating"));
        weeklyRepeating.setText(getString("weekly"));
        dailyRepeating.setText(getString("daily"));
        monthlyRepeating.setText(getString("monthly"));
        yearlyRepeating.setText(getString("yearly"));
        // Rapla 1.4: Initialize the split appointment button
        convertButton.setText(getString("appointment.convert"));
    }

    private void switchRepeatings()
    {
        UndoRepeatingTypeChange repeatingCommand = new UndoRepeatingTypeChange(savedRepeatingType, getCurrentRepeatingType());
        commandHistory.storeAndExecute(repeatingCommand);
    }

    public void addExceptions(List<TimeInterval> addedException)
    {
        UndoExceptionChange command = new UndoExceptionChange(addedException, new Object[] {} );
        commandHistory.storeAndExecute(command);
    }

    public void setAppointment(Appointment appointment)
    {
        this.appointment = appointment;
        this.repeating = appointment.getRepeating();

        if (appointment.getRepeating() != null)
        {
            repeatingEditor.mapFromAppointment();
            repeatingCard.show(repeatingContainer, "1");
            selectRepeatingChoiceButton(RepeatingRuleProjector.choiceFor(repeating.getType()));
        }
        else
        {
            singleEditor.mapFromAppointment();
            repeatingCard.show(repeatingContainer, "0");
            noRepeating.setSelected(true);
        }
        savedRepeatingType = getCurrentRepeatingType();
    }

    /** Map a {@link RepeatingRuleProjector.RepeatingChoice} to the corresponding
     *  radio button. View-side counterpart of {@code choiceFor(...)}. */
    private void selectRepeatingChoiceButton(RepeatingRuleProjector.RepeatingChoice choice)
    {
        switch (choice)
        {
            case NONE    -> noRepeating.setSelected(true);
            case DAILY   -> dailyRepeating.setSelected(true);
            case WEEKLY  -> weeklyRepeating.setSelected(true);
            case MONTHLY -> monthlyRepeating.setSelected(true);
            case YEARLY  -> yearlyRepeating.setSelected(true);
        }
    }

    List<Consumer<Appointment>> appointmentChangedConsumer = new ArrayList<>();
    @Override
    public void init(Consumer<Appointment> appointmentChanged) {
        this.appointmentChangedConsumer.add( appointmentChanged);
    }

    @Override
    public CommandHistory getCommandHistory() {
        return commandHistory;
    }

    public Appointment getAppointment()
    {
        return appointment;
    }

    @Override
    public void appointmentChanged() {
    }

    public void setSelectedEditDate(LocalDateTime selectedEditDate)
    {
        this.selectedEditDate = selectedEditDate;
    }

    public LocalDateTime getSelectedEditDate()
    {
        return selectedEditDate;
    }

    public void dispose()
    {
        singleEditor.dispose();
        repeatingEditor.dispose();
    }

    public JComponent getComponent()
    {
        return panel;
    }

    /**
     * registers new ChangeListener for this component. An ChangeEvent will be
     * fired to every registered ChangeListener when the appointment changes.
     *
     * @see javax.swing.event.ChangeListener
     * @see javax.swing.event.ChangeEvent
     */
    public void addChangeListener(ChangeListener listener)
    {
        listenerList.add(listener);
    }

    /** removes a listener from this component. */
    public void removeChangeListener(ChangeListener listener)
    {
        listenerList.remove(listener);
    }

    public ChangeListener[] getChangeListeners()
    {
        return listenerList.toArray(new ChangeListener[] {});
    }

    protected void fireAppointmentChanged()
    {
        if (listenerList.size() == 0)
            return;
        ChangeEvent evt = new ChangeEvent(this);
        ChangeListener[] listeners = getChangeListeners();
        for (int i = 0; i < listeners.length; i++)
        {
            listeners[i].stateChanged(evt);
        }
        repeatingEditor.updateExceptionCount();
        getLogger().debug("appointment changed: " + appointment);
    }

    static double ROW_SIZE = 21;

    private void setToWholeDays(final boolean oneDayEvent)
    {
        boolean wasSet = appointment.isWholeDaysSet();
        appointment.setWholeDays(oneDayEvent);
        if (wasSet && !oneDayEvent)
        {
            // BJO 00000070
            CalendarOptions calenderOptions = getCalendarOptions();
            int startMinutes = calenderOptions.getWorktimeStartMinutes();
            int endMinutes = calenderOptions.getWorktimeEndMinutes();
            LocalDateTime start = appointment.getStart().plusMinutes(startMinutes);
            LocalDateTime end = appointment.getEnd().minusDays(1).plusMinutes(endMinutes);
            // BJO 00000070
            if (end.isBefore(start))
            {
                end = DateTools.addDay(end);
            }
            appointment.move(start, end);
        }
    }

    class SingleEditor implements DateChangeListener, Disposable
    {
        JPanel content = new JPanel();
        JLabel startLabel = new JLabel();
        RaplaCalendar startDate;
        JLabel startTimeLabel = new JLabel();
        RaplaTime startTime;
        JLabel endLabel = new JLabel();
        RaplaCalendar endDate;
        JLabel endTimeLabel = new JLabel();
        RaplaTime endTime;
        JCheckBox oneDayEventCheckBox = new JCheckBox();
        private boolean listenerEnabled = true;

        public SingleEditor()
        {
            double pre = TableLayout.PREFERRED;
            double[][] size = { { pre, 5, pre, 10, pre, 5, pre, 5, pre }, // Columns
                    { ROW_SIZE, 6, ROW_SIZE, 6, ROW_SIZE } }; // Rows
            TableLayout tableLayout = new TableLayout(size);
            content.setLayout(tableLayout);
        }

        public void initialize()
        {
            startDate = createRaplaCalendar(dateRenderer, ioInterface);
            endDate = createRaplaCalendar(dateRenderer, ioInterface);
            startTime = createRaplaTime(ioInterface);
            endTime = createRaplaTime(ioInterface);
            content.add(startLabel, "0,0,r,f");
            startLabel.setText(getString("start_date"));
            startTimeLabel.setText(getString("time_at"));
            endTimeLabel.setText(getString("time_at"));
            content.add(startDate, "2,0,f,f");
            content.add(startTimeLabel, "4,0,l,f");
            content.add(startTime, "6,0,f,f");

            content.add(endLabel, "0,2,r,f");
            endLabel.setText(getString("end_date"));
            content.add(endDate, "2,2,f,f");
            content.add(endTimeLabel, "4,2,r,f");
            content.add(endTime, "6,2,f,f");

            oneDayEventCheckBox.setText(getString("all-day"));
            content.add(oneDayEventCheckBox, "8,0");
            oneDayEventCheckBox.addItemListener(itemevent -> {
                boolean selected = itemevent.getStateChange() == ItemEvent.SELECTED;
                setToWholeDays(selected);
                processChange(itemevent.getSource());
            });

            startDate.addDateChangeListener(this);
            startTime.addDateChangeListener(this);
            endDate.addDateChangeListener(this);
            endTime.addDateChangeListener(this);
        }

        public JComponent getComponent()
        {
            return content;
        }

        public void dispose()
        {
        }

        public void dateChanged(DateChangeEvent evt)
        {
            final Object source = evt.getSource();
            processChange(source);
        }

        private void processChange(final Object source)
        {
            if (!listenerEnabled)
                return;
            try
            {
                listenerEnabled = false;
                RaplaLocale raplaLocale = getRaplaLocale();
                LocalDateTime appStart = appointment.getStart();
                LocalDateTime appEnd = appointment.getEnd();
                java.time.Duration duration = java.time.Duration.between(appStart, appEnd);
                boolean wholeDaysSet = appointment.isWholeDaysSet();
                boolean oneDayEventSelected = oneDayEventCheckBox.isSelected();
                if (source == startDate || source == startTime)
                {
                    LocalDateTime date = startDate.getDate();
                    LocalDateTime time = startTime.getTime();
                    LocalDateTime newStart = raplaLocale.toDate(date, time);
                    LocalDateTime newEnd = newStart.plus(duration);
                    if (newStart.equals(appStart) && newEnd.equals(appEnd))
                        return;
                    UndoSingleEditorChange command = new UndoSingleEditorChange(appStart, appEnd, wholeDaysSet, newStart, newEnd, oneDayEventSelected);
                    commandHistory.storeAndExecute(command);

                }
                if (source == endTime)
                {
                    LocalDateTime newEnd = raplaLocale.toDate(endDate.getDate(), endTime.getTime());
                    if (appStart.isAfter(newEnd))
                    {
                        newEnd = DateTools.addDay(newEnd);
                    }
                    if (newEnd.equals(appEnd))
                        return;
                    UndoSingleEditorChange command = new UndoSingleEditorChange(null, appEnd, wholeDaysSet, null, newEnd, oneDayEventSelected);
                    commandHistory.storeAndExecute(command);
                }
                if (source == endDate)
                {
                    LocalDateTime newEnd = raplaLocale.toDate(DateTools.addDays(endDate.getDate(), oneDayEventSelected ? 1 : 0), endTime.getTime());
                    LocalDateTime newStart = null;
                    if (appStart.isAfter(newEnd) || (oneDayEventSelected && !appStart.isBefore(newEnd)))
                    {
                        long mod = duration.toMillis() % DateTools.MILLISECONDS_PER_DAY;
                        if (mod != 0)
                        {
                            newStart = newEnd.minus(java.time.Duration.ofMillis(mod));
                        }
                        else
                        {
                            newStart = DateTools.addDays(newEnd, -1);
                        }
                    }
                    UndoSingleEditorChange command = new UndoSingleEditorChange(appStart, appEnd, wholeDaysSet, newStart, newEnd, oneDayEventSelected);
                    commandHistory.storeAndExecute(command);
                }
                if (source == oneDayEventCheckBox)
                {
                    LocalDateTime date = startDate.getDate();
                    LocalDateTime time = startTime.getTime();
                    LocalDateTime oldStart = raplaLocale.toDate(date, time);
                    LocalDateTime oldEnd = raplaLocale.toDate(endDate.getDate(), endTime.getTime());
                    UndoSingleEditorChange command = new UndoSingleEditorChange(oldStart, oldEnd, !oneDayEventSelected, appStart, appEnd, oneDayEventSelected);
                    commandHistory.storeAndExecute(command);

                }
            }
            finally
            {
                listenerEnabled = true;
            }
        }

        private void mapFromAppointment()
        {
            listenerEnabled = false;
            try
            {
                final boolean wholeDaysSet = appointment.isWholeDaysSet();
                LocalDateTime start = appointment.getStart();
                startDate.setDate(start);
                LocalDateTime end = appointment.getEnd();
                endDate.setDate(DateTools.addDays(end, wholeDaysSet ? -1 : 0));
                endTime.setDurationStart(DateTools.isSameDay(start, end) ? start : null);
                startTime.setTime(start);
                endTime.setTime(end);
                oneDayEventCheckBox.setSelected(wholeDaysSet);
                startTimeLabel.setVisible(!wholeDaysSet);
                startTime.setVisible(!wholeDaysSet);
                endTime.setVisible(!wholeDaysSet);
                endTimeLabel.setVisible(!wholeDaysSet);
                for (Consumer<Appointment> consumer: appointmentChangedConsumer) {
                    consumer.accept(appointment);
                }
            }
            finally
            {
                listenerEnabled = true;
            }
            convertButton.setEnabled(false);
        }

        private void mapToAppointment()
        {
            RaplaLocale raplaLocale = getRaplaLocale();
            LocalDateTime start = raplaLocale.toDate(startDate.getDate(), startTime.getTime());
            LocalDateTime end = raplaLocale.toDate(endDate.getDate(), endTime.getTime());
            if (oneDayEventCheckBox.isSelected())
            {
                end = raplaLocale.toDate(DateTools.addDay(endDate.getDate()), endTime.getTime());
            }
            appointment.move(start, end);
            fireAppointmentChanged();
        }

        /**
         * This class collects any information of changes done in the fields when a single
         * appointment is selected.
         * This is where undo/redo for the fields within a single-appointment at right of the edit view
         * is realized.
         * @author Jens Fritz
         *
         */

        //Erstellt von Dominik Krickl-Vorreiter
        public class UndoSingleEditorChange implements CommandUndo<RuntimeException>
        {
            LocalDateTime oldStart;
            LocalDateTime oldEnd;
            boolean oldoneDay;

            LocalDateTime newStart;
            LocalDateTime newEnd;
            boolean newoneDay;

            public UndoSingleEditorChange(LocalDateTime oldstart, LocalDateTime oldend, boolean oldoneDay, LocalDateTime newstart, LocalDateTime newend, boolean newoneDay)
            {
                this.oldStart = oldstart;
                this.oldEnd = oldend;
                this.oldoneDay = oldoneDay;

                this.newStart = newstart;
                this.newEnd = newend;
                this.newoneDay = newoneDay;
            }

            public Promise<Void> execute()
            {
                listenerEnabled = false;
                if (newStart != null && oldStart != null)
                {
                    startTime.setTime(newStart);
                    startDate.setDate(newStart);
                    getLogger().debug("Starttime/-date adjusted");
                }
                if (newEnd != null && oldEnd != null)
                {
                    endTime.setTime(newEnd);
                    endDate.setDate(DateTools.addDays(newEnd, newoneDay ? -1 : 0));
                    getLogger().debug("Endtime/-date adjusted");
                }
                if (oldoneDay != newoneDay)
                {
                    startTime.setVisible(!newoneDay);
                    startTimeLabel.setVisible(!newoneDay);
                    endTime.setVisible(!newoneDay);
                    endTimeLabel.setVisible(!newoneDay);
                    oneDayEventCheckBox.setSelected(newoneDay);
                    getLogger().debug("Whole day adjusted");
                }
                mapToAppointment();
                getLogger().debug("SingleEditor adjusted");
                mapFromAppointment();
                listenerEnabled = true;
                return ResolvedPromise.VOID_PROMISE;
            }

            public Promise<Void> undo()
            {
                listenerEnabled = false;
                if (oldStart != null && newStart != null)
                {
                    startTime.setTime(oldStart);
                    startDate.setDate(oldStart);
                    getLogger().debug("Starttime/-date undo");
                }
                if (oldEnd != null && newEnd != null)
                {
                    endTime.setTime(oldEnd);
                    endDate.setDate(oldEnd);
                    getLogger().debug("Endtime/-date undo");
                }
                if (oldoneDay != newoneDay)
                {
                    startTime.setVisible(!oldoneDay);
                    startTimeLabel.setVisible(!oldoneDay);
                    endTime.setVisible(!oldoneDay);
                    endTimeLabel.setVisible(!oldoneDay);
                    oneDayEventCheckBox.setSelected(oldoneDay);
                    getLogger().debug("Whole day undo");
                }
                mapToAppointment();
                getLogger().debug("SingleEditor undo");
                mapFromAppointment();
                listenerEnabled = true;
                return ResolvedPromise.VOID_PROMISE;
            }

            public String getCommandoName()
            {
                return getString("change") + " " + getString("appointment");
            }

        }
    }

    private RaplaCalendar createRaplaCalendar(DateRenderer dateRenderer, IOInterface ioInterface)
    {
        return RaplaGUIComponent.createRaplaCalendar(dateRenderer,ioInterface,getI18n(),getRaplaLocale(), getLogger());
    }

    class RepeatingEditor implements ActionListener, DateChangeListener, ChangeListener, Disposable
    {
        JPanel content = new JPanel();

        JPanel intervalPanel = new JPanel();
        JPanel weekdayInMonthPanel = new JPanel();
        JPanel dayInMonthPanel = new JPanel();

        RaplaNumber interval = new RaplaNumber(null, RaplaNumber.ONE, null, false);

        {
            addCopyPaste(interval.getNumberField(), getI18n(), getRaplaLocale(), ioInterface, getLogger());
        }

        RaplaNumber weekdayInMonth = new RaplaNumber(null, RaplaNumber.ONE, Integer.valueOf(5), false);

        {
            addCopyPaste(weekdayInMonth.getNumberField(), getI18n(), getRaplaLocale(), ioInterface, getLogger());
        }

        RaplaNumber dayInMonth = new RaplaNumber(null, RaplaNumber.ONE, Integer.valueOf(31), false);

        {
            addCopyPaste(dayInMonth.getNumberField(), getI18n(), getRaplaLocale(), ioInterface, getLogger());
        }

        WeekdayMapper weekdayMapper = new WeekdayMapper(getRaplaLocale(), getCalendarOptions().getFirstDayOfWeek());
        WeekdayChooser weekdayChooser = new WeekdayChooser(weekdayMapper);
        JLabel dayLabel = new JLabel();

        JLabel startTimeLabel = new JLabel();
        RaplaTime startTime;
        JCheckBox oneDayEventCheckBox = new JCheckBox();

        JLabel endTimeLabel = new JLabel();
        JPanel endTimePanel = new JPanel();
        JPanel weekdaysPanel = new JPanel();

        RaplaTime endTime;
        public final int SAME_DAY = 0, NEXT_DAY = 1, X_DAYS = 2;
        JComboBox dayChooser;
        RaplaNumber days = new RaplaNumber(null, Integer.valueOf(2), null, false);

        {
            addCopyPaste(days.getNumberField(), getI18n(), getRaplaLocale(), ioInterface, getLogger());
        }

        JLabel startDateLabel = new JLabel();
        RaplaCalendar startDate;
        PeriodChooser startDatePeriod;

        JComboBox endingChooser;
        public final int REPEAT_UNTIL = 0, REPEAT_N_TIMES = 1, REPEAT_FOREVER = 2;
        RaplaCalendar endDate;
        JPanel numberPanel = new JPanel();
        RaplaNumber number = new RaplaNumber(null, RaplaNumber.ONE, null, false);

        {
            addCopyPaste(number.getNumberField(), getI18n(), getRaplaLocale(), ioInterface, getLogger());
        }

        JPanel endDatePeriodPanel = new JPanel();
        PeriodChooser endDatePeriod;

        RaplaButton exceptionButton = new RaplaButton();
        ExceptionEditor exceptionEditor;
        DialogInterface exceptionDlg;
        MonthChooser monthChooser = new MonthChooser();
        private boolean listenerEnabled = true;
        Map<Integer,JCheckBox> weekdaysChecker = new LinkedHashMap<>();

        public RepeatingEditor() throws RaplaException
        {
            startDatePeriod = new PeriodChooser(getI18n(), getFacade(), PeriodChooser.START_ONLY);
            endDatePeriod = new PeriodChooser(getI18n(), getFacade(), PeriodChooser.END_ONLY);
            // Create a TableLayout for the frame
            double pre = TableLayout.PREFERRED;
            double fill = TableLayout.FILL;
            double[][] size = { { pre, 5, pre, 5, fill }, // Columns
                    { ROW_SIZE, 1, 18, 10, ROW_SIZE, 5, ROW_SIZE, 15, ROW_SIZE, 6, ROW_SIZE, 0 } }; // Rows
            TableLayout tableLayout = new TableLayout(size);
            content.setLayout(tableLayout);
        }

        public Locale getLocale()
        {
            return getI18n().getLocale();
        }

        public JComponent getComponent()
        {
            return content;
        }

        public void initialize()
        {
            // Interval / Weekday
            interval.setColumns(2);
            weekdayInMonth.setColumns(2);
            dayInMonth.setColumns(2);
            intervalPanel.setLayout(new BoxLayout(intervalPanel, BoxLayout.X_AXIS));
            intervalPanel.add(new JLabel(getString("repeating.interval.pre") + " "));
            intervalPanel.add(Box.createHorizontalStrut(3));
            intervalPanel.add(interval);
            intervalPanel.add(Box.createHorizontalStrut(3));
            intervalPanel.add(new JLabel(getString("repeating.interval.post")));

            dayInMonthPanel.setLayout(new BoxLayout(dayInMonthPanel, BoxLayout.X_AXIS));
            // dayInMonthPanel.add(new JLabel("Am"));
            dayInMonthPanel.add(Box.createHorizontalStrut(35));
            dayInMonthPanel.add(dayInMonth);
            dayInMonthPanel.add(Box.createHorizontalStrut(3));
            dayInMonthPanel.add(new JLabel(getString("repeating.interval.post")));

            weekdayInMonthPanel.setLayout(new BoxLayout(weekdayInMonthPanel, BoxLayout.X_AXIS));
            // weekdayInMonthPanel.add(new JLabel("Am"));
            weekdayInMonthPanel.add(Box.createHorizontalStrut(35));
            weekdayInMonthPanel.add(weekdayInMonth);
            weekdayInMonthPanel.add(Box.createHorizontalStrut(3));
            weekdayInMonthPanel.add(new JLabel(getString("repeating.interval.post")));

            interval.addChangeListener(this);
            weekdayInMonth.addChangeListener(this);
            dayInMonth.addChangeListener(this);
            weekdayChooser.setLocale(getLocale());
            weekdayChooser.addActionListener(this);

            monthChooser.setLocale(getLocale());
            monthChooser.addActionListener(this);
            dayLabel.setText(getString("day") + " ");
            dayLabel.setVisible(false);

            // StartTime
            startTimeLabel.setText(getString("start_time"));
            startTime = createRaplaTime(ioInterface);
            startTime.addDateChangeListener(this);
            oneDayEventCheckBox.setText(getString("all-day"));

            oneDayEventCheckBox.addActionListener(this);
            // EndTime duration
            endTimeLabel.setText(getString("end_time"));
            endTime = createRaplaTime(ioInterface);
            endTime.addDateChangeListener(this);
            @SuppressWarnings("unchecked")
            JComboBox jComboBox = new JComboBox(
                    new String[] { getString("appointment.same_day"), getString("appointment.next_day"), getString("appointment.day_x") });
            dayChooser = jComboBox;
            dayChooser.addActionListener(this);
            days.setColumns(2);
            endTimePanel.setLayout(new TableLayout(new double[][] { { TableLayout.PREFERRED, 5, TableLayout.PREFERRED, TableLayout.FILL }, { ROW_SIZE } }));
            // endTimePanel.add(endTime,"0,0,l,f");
            endTimePanel.add(dayChooser, "0,0");
            endTimePanel.add(days, "2,0");
            days.setVisible(false);
            days.addChangeListener(this);

            // start-date (with period-box)
            startDatePeriod.addActionListener(this);
            startDateLabel.setText(getString("repeating.start_date"));
            startDate = createRaplaCalendar(dateRenderer, ioInterface);
            startDate.addDateChangeListener(this);

            // end-date (with period-box)/n-times/forever
            endDatePeriod.addActionListener(this);
            endDate = createRaplaCalendar(dateRenderer, ioInterface);
            endDate.addDateChangeListener(this);

            @SuppressWarnings("unchecked")
            JComboBox jComboBox2 = new JComboBox(new RepeatingEnding[] { RepeatingEnding.END_DATE, RepeatingEnding.N_TIMES, RepeatingEnding.FOREVEVER });
            endingChooser = jComboBox2;
            endingChooser.addActionListener(this);

            number.setColumns(3);
            number.setNumber(Integer.valueOf(1));
            number.addChangeListener(this);
            numberPanel.setLayout(new BorderLayout());
            numberPanel.add(number, BorderLayout.WEST);
            numberPanel.setVisible(false);
            intervalPanel.setVisible(false);
            weekdayInMonthPanel.setVisible(false);
            dayInMonthPanel.setVisible(false);

            // exception
            exceptionButton.setText(getString("appointment.exceptions") + " (0)");
            exceptionButton.addActionListener(this);

            content.add(intervalPanel, "0,0,l,f");
            content.add(weekdayInMonthPanel, "0,0,l,f");
            content.add(dayInMonthPanel, "0,0,l,f");
            content.add(weekdayChooser, "2,0,f,f");
            final BoxLayout mgr = new BoxLayout(weekdaysPanel, BoxLayout.X_AXIS);

            weekdaysPanel.setLayout(mgr);

            final String[] shortWeekdays = getRaplaLocale().getFormats().getShortWeekdays();
            for (int i = 0; i < 7; i++)
            {
                final int weekday = weekdayMapper.dayForIndex(i);
                String name = shortWeekdays[weekday];
                if (name.length() > 2)
                {
                    name = name.substring(0, 2);
                }
                final JCheckBox checkBox = new JCheckBox(name);
                weekdaysChecker.put( weekday, checkBox);
                checkBox.addActionListener( this );
                checkBox.setPreferredSize(new Dimension(20, 10));
                weekdaysPanel.add(checkBox);
            }
            content.add(weekdaysPanel, "2,2,4,2,l,t");
            content.add(monthChooser, "2,0,f,f");
            content.add(dayLabel, "2,0,l,f");

            content.add(startTimeLabel, "0,4,l,f");
            content.add(startTime, "2,4,f,f");
            content.add(oneDayEventCheckBox, "4,4");

            content.add(exceptionButton, "4,0,r,t");

            content.add(endTimeLabel, "0,6,l,f");
            content.add(endTime, "2,6,f,f");
            content.add(endTimePanel, "4,6,4,6,l,f");

            content.add(startDateLabel, "0,8,l,f");
            content.add(startDate, "2,8,l,f");
            content.add(startDatePeriod, "4,8,f,f");

            content.add(endingChooser, "0,10,l,f");
            content.add(endDate, "2,10,l,f");
            content.add(endDatePeriodPanel, "4,10,f,f");
            // We must surround the endDatePeriod with a panel to
            // separate visiblity of periods from visibility of the panel
            endDatePeriodPanel.setLayout(new BorderLayout());
            endDatePeriodPanel.add(endDatePeriod, BorderLayout.CENTER);
            content.add(numberPanel, "2,10,f,f");

            setRenderer(endingChooser, new ListRenderer());
            // Rapla 1.4: Initialize the split appointment button
            convertButton.addActionListener(this);

            // content.add(exceptionLabel,"0,10,l,c");
            // content.add(exceptionPanel,"2,10,4,10,l,c");
        }

        public void dispose()
        {
            endDatePeriod.removeActionListener(this);
            startDatePeriod.removeActionListener(this);
        }

        private LocalDateTime getStart()
        {
            LocalDateTime start = getRaplaLocale().toDate(startDate.getDate(), startTime.getTime());
            /*
             * if (repeating.isWeekly() || repeating.isMonthly()) { Calendar
			 * calendar = getRaplaLocale().createCalendar();
			 * calendar.setTime(start); calendar.set(Calendar.DAY_OF_WEEK,
			 * weekdayChooser.getSelectedWeekday() ); if
			 * (calendar.getTime().before(start)) {
			 * calendar.add(Calendar.DAY_OF_WEEK,7); } start =
			 * calendar.getTime(); } if (repeating.isYearly()) { Calendar
			 * calendar = getRaplaLocale().createCalendar();
			 * calendar.setTime(start); calendar.set(Calendar.MONTH,
			 * monthChooser.getSelectedMonth() );
			 * calendar.set(Calendar.DAY_OF_MONTH,
			 * dayInMonth.getNumber().intValue() ); start = calendar.getTime();
			 * 
			 * }
			 */
            return start;
        }

        private LocalDateTime getEnd()
        {
            LocalDateTime end = getRaplaLocale().toDate(getStart(), endTime.getTime());
            if (dayChooser.getSelectedIndex() == NEXT_DAY)
                end = DateTools.addDay(end);
            if (dayChooser.getSelectedIndex() == X_DAYS)
                end = DateTools.addDays(end, days.getNumber().intValue());
            return end;
        }

        public void actionPerformed(ActionEvent evt)
        {
            if (evt.getSource() == exceptionButton)
            {
                try
                {
                    showExceptionDlg();
                }
                catch (RaplaException ex)
                {
                    dialogUiFactory.showException(ex, new SwingPopupContext(content, null));
                }
                return;
            }

            if (!listenerEnabled)
                return;
            try
            {
                listenerEnabled = false;

                // Rapla 1.4: Split appointment button has been clicked
                if (evt.getSource() == convertButton)
                {
                    // Notify registered listeners
                    ActionListener[] listeners = listenerList.toArray(new ActionListener[] {});
                    for (int i = 0; i < listeners.length; i++)
                    {
                        listeners[i].actionPerformed(new ActionEvent(AppointmentController.this, ActionEvent.ACTION_PERFORMED, "split"));
                    }
                    return;
                }
                else if (evt.getSource() == endingChooser)
                {
                    // repeating has changed to UNTIL, the default endDate will
                    // be set
                    int index = endingChooser.getSelectedIndex();
                    if (index == REPEAT_UNTIL)
                    {
                        LocalDateTime slotDate = getSelectedEditDate();
                        if (slotDate != null)
                            endDate.setDate(slotDate);
                    }
                }
                else if (evt.getSource() == weekdayChooser)
                {
                    int weekday = weekdayChooser.getSelectedWeekday();
                    final LocalDateTime date = DateTools.setWeekday(startDate.getDate(), weekday);
                    startDate.setDate(date);
                    resetWeekdays(weekday);
                }
                else if (evt.getSource() == monthChooser)
                {
                    int year = DateTools.getYear(startDate.getDate());
                    final long l = DateTools.toDate(year, monthChooser.getSelectedMonth(), dayInMonth.getNumber().intValue());
                    startDate.setDate(DateTools.toLocalDateTime(l));
                }
                else if (evt.getSource() == dayChooser)
                {
                    if (dayChooser.getSelectedIndex() == SAME_DAY)
                    {
                        if (getEnd().isBefore(getStart()))
                        {
                            endTime.setTime(getStart());
                            getLogger().debug("endtime adjusted");
                        }
                    }

                }
                else if (evt.getSource() == startDatePeriod && startDatePeriod.getPeriod() != null)
                {
                    LocalDateTime date = startDatePeriod.getPeriod().getStart();
                    if (repeating.isWeekly() || repeating.isMonthly())
                    {
                        int selectedWeekday = weekdayChooser.getSelectedWeekday();
                        if (selectedWeekday == 1)
                        {
                            date = DateTools.setWeekday(date, selectedWeekday);
                            if (date.isBefore(startDatePeriod.getPeriod().getStart()))
                            {
                                date = DateTools.addWeeks(date, 1);
                            }
                        }
                    }
                    getLogger().debug("startdate adjusted to period");
                    startDate.setDate(date);
                    endDatePeriod.setSelectedPeriod(startDatePeriod.getPeriod());
                    resetWeekdays(DateTools.getWeekday( date));
                }
                else if (evt.getSource() == endDatePeriod && endDatePeriod.getDate() != null)
                {
                    endDate.setDate(DateTools.subDay(endDatePeriod.getDate()));
                    getLogger().debug("enddate adjusted to period");
                }

                doChanges();
            }
            finally
            {
                listenerEnabled = true;
            }
        }

        private void resetWeekdays(int weekday)
        {
            Map<Integer, WeekdaySelection> sels = RepeatingRuleProjector.weekdaysOnAnchorShift(
                    repeating.getWeekdays(), weekday, weekdaysChecker.keySet());
            for (Map.Entry<Integer, JCheckBox> entry : weekdaysChecker.entrySet())
            {
                WeekdaySelection s = sels.get(entry.getKey());
                if (s == null) continue;
                entry.getValue().setSelected(s.selected());
                entry.getValue().setEnabled(s.enabled());
            }
        }

        public void stateChanged(ChangeEvent evt)
        {
            if (!listenerEnabled)
                return;
            try
            {
                listenerEnabled = false;

                if (evt.getSource() == weekdayInMonth && repeating.isMonthly())
                {
                    Number weekdayOfMonthValue = weekdayInMonth.getNumber();
                    if (weekdayOfMonthValue != null && repeating.isMonthly())
                    {
                        final int weekday = weekdayOfMonthValue.intValue();
                        final LocalDateTime date = DateTools.setWeekday(appointment.getStart(), weekday);
                        startDate.setDate(date);
                    }
                }
                if (evt.getSource() == dayInMonth && repeating.isYearly())
                {
                    Number dayOfMonthValue = dayInMonth.getNumber();
                    if (dayOfMonthValue != null && repeating.isYearly())
                    {
                        final DateTools.DateWithoutTimezone dateWithoutTimezone = DateTools.toDate(appointment.getStart());
                        final long l = DateTools.toTime(dateWithoutTimezone.year, dateWithoutTimezone.month, dayOfMonthValue.intValue());
                        startDate.setDate(DateTools.toLocalDateTime(l));
                    }
                }

                doChanges();
            }
            finally
            {
                listenerEnabled = true;
            }
        }

        public void dateChanged(DateChangeEvent evt)
        {
            if (!listenerEnabled)
                return;
            try
            {
                listenerEnabled = false;

                java.time.Duration duration = java.time.Duration.between(appointment.getStart(), appointment.getEnd());
                if (evt.getSource() == startTime)
                {
                    LocalDateTime newEnd = getStart().plus(duration);
                    endTime.setTime(newEnd);
                    getLogger().debug("endtime adjusted");
                }
                else if (evt.getSource() == endTime)
                {
                    LocalDateTime newEnd = getEnd();
                    if (getStart().isAfter(newEnd))
                    {
                        newEnd = DateTools.addDay(newEnd);
                        endTime.setTime(newEnd);
                        getLogger().debug("enddate adjusted");
                    }
                }
                else
                {
                    int weekday = DateTools.getWeekday( startDate.getDate());
                    resetWeekdays(weekday);

                }

                doChanges();
            }
            finally
            {
                listenerEnabled = true;
            }
        }

        private void mapToAppointment()
        {
            final EndingMode mode = switch (endingChooser.getSelectedIndex())
            {
                case REPEAT_UNTIL   -> EndingMode.UNTIL;
                case REPEAT_N_TIMES -> EndingMode.N_TIMES;
                default             -> EndingMode.FOREVER;
            };

            // Preserve original side-effect: snap the visible end-date widget
            // back to the start date when the user picked an earlier end.
            if (mode == EndingMode.UNTIL
                    && DateTools.countDays(startDate.getDate(), endDate.getDate()) < 0)
            {
                endDate.setDate(startDate.getDate());
            }

            final Number intervalValue = interval.getNumber();
            final Number numberValue = number.getNumber();
            final RepeatingRuleModel model = new RepeatingRuleModel(
                    repeating.getType(),
                    intervalValue != null ? intervalValue.intValue() : 1,
                    collectCheckedWeekdays(),
                    mode,
                    mode == EndingMode.UNTIL ? endDate.getDate() : null,
                    numberValue != null ? numberValue.intValue() : 1);
            RepeatingRuleWriter.writeTo(model, repeating, getStart());

            appointment.move(getStart(), getEnd());
            // setToWholeDays runs after move to avoid resetting the dates
            setToWholeDays(oneDayEventCheckBox.isSelected());
        }

        private Set<Integer> collectCheckedWeekdays()
        {
            if (!repeating.isWeekly())
            {
                return java.util.Collections.emptySet();
            }
            Set<Integer> weekdays = new TreeSet<>();
            for (Map.Entry<Integer, JCheckBox> entry : weekdaysChecker.entrySet())
            {
                if (entry.getValue().isSelected())
                {
                    weekdays.add(entry.getKey());
                }
            }
            return weekdays;
        }

        private void updateExceptionCount()
        {
            ExceptionButtonState state = RepeatingRuleProjector.exceptionButtonState(
                    getString("appointment.exceptions"), repeating);
            exceptionButton.setForeground(state.style() == ExceptionCountStyle.HIGHLIGHTED
                    ? Color.red
                    : UIManager.getColor("Label.foreground"));
            exceptionButton.setText(state.label());
        }

        private void showEnding(int index)
        {
            EndingMode mode = switch (index)
            {
                case REPEAT_UNTIL   -> EndingMode.UNTIL;
                case REPEAT_N_TIMES -> EndingMode.N_TIMES;
                default             -> EndingMode.FOREVER;
            };
            EndingPanelVisibility v = RepeatingRuleProjector.endingPanelVisibility(mode, isPeriodVisible());
            endDate.setVisible(v.endDateVisible());
            endDatePeriodPanel.setVisible(v.endDatePeriodPanelVisible());
            numberPanel.setVisible(v.numberPanelVisible());
        }

        private void mapFromAppointment()
        {
            // closing is not necessary as dialog is modal
            //			if (exceptionDlg != null && exceptionDlg.isVisible())
            //				exceptionDlg.dispose();
            repeating = appointment.getRepeating();
            if (repeating == null)
            {
                return;
            }
            listenerEnabled = false;
            try
            {
                updateExceptionCount();
                if (exceptionEditor != null)
                    exceptionEditor.mapFromAppointment();

                interval.setNumber(Integer.valueOf(repeating.getInterval()));

                LocalDateTime start = appointment.getStart();
                LocalDate localStartDate = appointment.getStart().toLocalDate();
                startDate.setDate(start);
                startDatePeriod.setDate(start);
                startTime.setTime(start);
                LocalDateTime end = appointment.getEnd();
                endTime.setTime(end);
                endTime.setDurationStart(DateTools.isSameDay(start, end) ? start : null);

                final RepeatingPanelVisibility panels =
                        RepeatingRuleProjector.repeatingPanelVisibility(repeating, isPeriodVisible());
                weekdayInMonthPanel.setVisible(panels.weekdayInMonthPanelVisible());
                intervalPanel.setVisible(panels.intervalPanelVisible());
                dayInMonthPanel.setVisible(panels.dayInMonthPanelVisible());

                final EndingMode mode = RepeatingRuleProjector.endingMode(repeating);
                final EndDateBinding endBinding = RepeatingRuleProjector.endDateBinding(repeating);
                if (endBinding != null)
                {
                    endDate.setDate(endBinding.endDate());
                    endDatePeriod.setDate(endBinding.endDatePeriodDate());
                    number.setNumber(Integer.valueOf(endBinding.number()));
                }
                endingChooser.setSelectedIndex(switch (mode)
                {
                    case UNTIL   -> REPEAT_UNTIL;
                    case N_TIMES -> REPEAT_N_TIMES;
                    case FOREVER -> REPEAT_FOREVER;
                });
                showEnding(endingChooser.getSelectedIndex());

                startDatePeriod.setVisible(panels.startDatePeriodVisible());
                endDatePeriod.setVisible(panels.endDatePeriodVisible());
                weekdaysPanel.setVisible(panels.weekdaysPanelVisible());
                if (panels.weekdaysPanelVisible())
                {
                    final int startWeekday = DateTools.getWeekday(appointment.getStart());
                    final Map<Integer, WeekdaySelection> sels =
                            RepeatingRuleProjector.weekdaySelections(repeating, startWeekday, weekdaysChecker.keySet());
                    for (Map.Entry<Integer, JCheckBox> entry : weekdaysChecker.entrySet())
                    {
                        WeekdaySelection s = sels.get(entry.getKey());
                        if (s == null) continue;
                        entry.getValue().setSelected(s.selected());
                        entry.getValue().setEnabled(s.enabled());
                    }
                }

                dayLabel.setVisible(panels.dayLabelVisible());
                weekdayChooser.setVisible(panels.weekdayChooserVisible());
                monthChooser.setVisible(panels.monthChooserVisible());
                if (panels.weekdayChooserVisible())
                {
                    weekdayChooser.selectWeekday(DateTools.getWeekday(start));
                }
                if (panels.monthChooserVisible())
                {
                    monthChooser.selectMonth(DateTools.getMonth(start));
                    dayInMonth.setNumber(Integer.valueOf(DateTools.getDayOfMonth(start)));
                }
                if (repeating.isMonthly())
                {
                    weekdayInMonth.setNumber(Integer.valueOf(DateTools.getDayOfWeekInMonth(localStartDate)));
                }

                String typeString = repeating.getType().toString();
                startDateLabel.setText(getString(typeString) + " " + getString("repeating.start_date"));

                final DayChooserState dayState =
                        RepeatingRuleProjector.dayChooserState(DateTools.countDays(start, end));
                dayChooser.setSelectedIndex(switch (dayState.mode())
                {
                    case SAME_DAY -> SAME_DAY;
                    case NEXT_DAY -> NEXT_DAY;
                    case X_DAYS   -> X_DAYS;
                });
                if (dayState.mode() == RepeatingRuleProjector.DayChooserMode.X_DAYS)
                {
                    days.setNumber(Integer.valueOf(dayState.days()));
                }
                days.setVisible(dayState.daysVisible());
                final boolean wholeDaysSet = appointment.isWholeDaysSet();
                startTime.setEnabled(!wholeDaysSet);
                endTime.setEnabled(!wholeDaysSet);
                dayChooser.setEnabled(!wholeDaysSet);
                days.setEnabled(!wholeDaysSet);
                oneDayEventCheckBox.setSelected(wholeDaysSet);
                for (Consumer<Appointment> consumer: appointmentChangedConsumer) {
                    consumer.accept(appointment);
                }
            }
            finally
            {
                listenerEnabled = true;
            }
            convertButton.setEnabled(repeating.getEnd() != null);
            getComponent().revalidate();
        }

        private boolean isPeriodVisible()
        {
            try
            {
                PeriodModel periodModel = getFacade().getPeriodModel();
                return periodModel != null && periodModel.getSize() > 0;
            }
            catch (RaplaException e)
            {
                return false;
            }
        }

        private void showExceptionDlg() throws RaplaException
        {
            exceptionEditor = new ExceptionEditor();
            exceptionEditor.initialize();
            exceptionEditor.mapFromAppointment();
            exceptionEditor.getComponent().setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            final PopupContext popupContext = new SwingPopupContext(getComponent(), null);
            exceptionDlg = dialogUiFactory
                    .createContentDialog(popupContext, exceptionEditor.getComponent(), new String[] { getString("close") });
            exceptionDlg.setTitle(getString("appointment.exceptions"));
            exceptionDlg.start(true);
        }

        private void doChanges()
        {
            Appointment oldState = ((AppointmentImpl) appointment).clone();
            mapToAppointment();
            Appointment newState = ((AppointmentImpl) appointment).clone();
            UndoDataChange changeDataCommand = new UndoDataChange(oldState, newState);
            commandHistory.storeAndExecute(changeDataCommand);
        }
    }

    class ExceptionEditor implements ActionListener, ListSelectionListener
    {
        JPanel content = new JPanel();
        RaplaCalendar exceptionStart;
        RaplaCalendar exceptionEnd;
        RaplaButton addButton = new RaplaButton(RaplaButton.SMALL);
        RaplaButton removeButton = new RaplaButton(RaplaButton.SMALL);
        JList specialExceptions = new JList();
        private boolean listenersEnabled = true;

        public ExceptionEditor()
        {
            // Create a TableLayout for the frame
            double pre = TableLayout.PREFERRED;
            double min = TableLayout.MINIMUM;
            double fill = TableLayout.FILL;
            double yborder = 8;
            double[][] size = { { pre, pre, 0.1, 50, 100, 0.4, 15, 0.4 }, // Columns
                    { yborder, min, min, fill } }; // Rows
            TableLayout tableLayout = new TableLayout(size);
            content.setLayout(tableLayout);
        }

        public JComponent getComponent()
        {
            return content;
        }

        public void initialize()
        {
            addButton.setText(getString("add"));
            addButton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.arrow_right")));
            removeButton.setText(getString("remove"));
            removeButton.setIcon(RaplaImages.getIcon(i18n.getIcon("icon.arrow_left")));
            exceptionStart = createRaplaCalendar(dateRenderer, ioInterface);
            exceptionEnd = createRaplaCalendar(dateRenderer, ioInterface);
            /*
			 * this.add(new JLabel(getString("appointment.exception.general") +
			 * " "),"0,1"); this.add(new JScrollPane(generalExceptions
			 * ,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
			 * ,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) ,"1,1,1,3,t");
			 */
            JLabel label = new JLabel(getString("appointment.exception.days") + " ");
            label.setHorizontalAlignment(SwingConstants.RIGHT);
            content.add(label, "3,1,4,1,r,t");
            content.add(exceptionStart, "5,1,l,t");
            content.add(exceptionEnd, "7,1,l,t");
            content.add(addButton, "4,2,f,t");
            content.add(removeButton, "4,3,f,t");
            content.add(new JScrollPane(specialExceptions, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "5,2,7,3,t");

            addButton.addActionListener(this);
            removeButton.addActionListener(this);
            specialExceptions.addListSelectionListener(this);
            removeButton.setEnabled(false);
            specialExceptions.setFixedCellWidth(200);
            specialExceptions.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer()
            {
                private static final long serialVersionUID = 1L;

                public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
                {
                    if (value instanceof LocalDateTime)
                        value = getRaplaLocale().formatDateLong((LocalDateTime) value);
                    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                }
            };
            setRenderer(specialExceptions, cellRenderer);
            specialExceptions.addMouseListener(new MouseAdapter()
            {
                public void mouseClicked(MouseEvent evt)
                {
                    if (evt.getClickCount() > 1)
                    {
                        removeException();
                    }
                }
            });
            exceptionStart.addDateChangeListener((evt) ->
            {
                if (!listenersEnabled)
                {
                    return;
                }
                final LocalDateTime newDate = evt.getDate();
                if (newDate.isAfter(exceptionEnd.getDate()))
                {
                    try
                    {
                        listenersEnabled = false;
                        exceptionEnd.setDate(newDate);
                    }
                    finally
                    {
                        listenersEnabled = true;
                    }
                }
                updateExcetionDialogInterval();
            });

            exceptionEnd.addDateChangeListener((evt) ->
            {
                if (!listenersEnabled)
                {
                    return;
                }
                final LocalDateTime newDate = evt.getDate();
                if (newDate.isBefore(exceptionStart.getDate()))
                {
                    try
                    {
                        listenersEnabled = false;
                        exceptionStart.setDate(newDate);
                    }
                    finally
                    {
                        listenersEnabled = true;
                    }
                }
                updateExcetionDialogInterval();

            });
        }

        private void updateExcetionDialogInterval()
        {
            lastModifiedExceptionDialogInterval = new TimeInterval(exceptionStart.getDate(), exceptionEnd.getDate());
        }

        @SuppressWarnings("unchecked")
        public void mapFromAppointment()
        {
            LocalDateTime exceptDate = getSelectedEditDate();
            // only change exceptions dates if no execptions were changed with the exception dialog.
            // so we can keep the exception interval across multiple appointments
            try
            {
                listenersEnabled = false;
                if (lastModifiedExceptionDialogInterval == null)
                {
                    if (exceptDate != null)
                    {
                        exceptDate = DateTools.cutDate(exceptDate);
                        exceptionStart.setDate(exceptDate);
                        exceptionEnd.setDate(exceptDate);
                    }
                    else
                    {
                        exceptionStart.setDate(appointment.getStart());
                    }
                }
                else
                {
                    exceptionStart.setDate( lastModifiedExceptionDialogInterval.getStart());
                    exceptionEnd.setDate( lastModifiedExceptionDialogInterval.getEnd());
                }
            }
            finally
            {
                listenersEnabled = true;
            }

            if (repeating == null)
            {
                specialExceptions.setListData(new Object[0]);
            }
            else
            {
                LocalDateTime[] specialExceptionDates = repeating.getExceptions();
                specialExceptions.setListData(specialExceptionDates);
                for (int i = 0; i < specialExceptionDates.length; i++)
                {
                    LocalDateTime specialExceptionDate = specialExceptionDates[i];
                    if (specialExceptionDate.equals(exceptDate))
                    {
                        specialExceptions.setSelectedIndex(i);
                        try
                        {
                            specialExceptions.ensureIndexIsVisible(i);
                        }
                        catch (Exception ex)
                        {
                            // not sure if exception is thrown if special exceptions dialog not visible
                        }
                        break;
                    }
                }
            }
        }

        public void actionPerformed(ActionEvent evt)
        {
            if (evt.getSource() == addButton)
            {
                addException();
            }
            if (evt.getSource() == removeButton)
            {
                removeException();
            }
        }

        private void addException()
        {
            LocalDateTime exceptionStart = this.exceptionStart.getDate();
            LocalDateTime exceptionEnd = DateTools.fillDate(this.exceptionEnd.getDate());
            final TimeInterval timeInterval = new TimeInterval(exceptionStart, exceptionEnd);
            final List<TimeInterval> addedException = Collections.singletonList(timeInterval);
            addExceptions(addedException);
        }

        @SuppressWarnings("deprecation")
        private void removeException()
        {
            if (specialExceptions.getSelectedValues() == null)
                return;
            Object[] selectedExceptions = specialExceptions.getSelectedValues();
            UndoExceptionChange command = new UndoExceptionChange(Collections.emptyList(), selectedExceptions);
            commandHistory.storeAndExecute(command);
        }

        public void valueChanged(ListSelectionEvent e)
        {
            if (e.getSource() == specialExceptions)
            {
                removeButton.setEnabled(specialExceptions.getSelectedValue() != null);
            }
        }

    }

    /**
     * This class collects any information of changes done to the exceptions
     * of an appointment, if a repeating-type is selected.
     * This is where undo/redo for the changes of the exceptions within a repeating-type-appointment
     * at the right of the edit view is realized.
     * @author Jens Fritz
     *
     */
    public class UndoExceptionChange implements CommandUndo<RuntimeException>
    {
        boolean add;
        Collection<TimeInterval> exceptions;

        Object[] removedExceptions;

        public UndoExceptionChange(Collection<TimeInterval> addedException, Object[] removedExceptions)
        {
            this.add = !addedException.isEmpty() ;
            this.removedExceptions = removedExceptions;
            this.exceptions = addedException;
        }

        @SuppressWarnings("unchecked")
        public Promise<Void> execute()
        {
            Repeating repeating = appointment.getRepeating();
            if (repeating == null)
            {
                return ResolvedPromise.VOID_PROMISE;
            }
            if (add)
            {
                exceptions.forEach( repeating::addExceptions);
                updateExcpetionEditor(repeating);
                fireAppointmentChanged();
            }
            else
            {
                for (int i = 0; i < removedExceptions.length; i++)
                {
                    repeating.removeException((LocalDateTime) removedExceptions[i]);
                }
                updateExcpetionEditor(repeating);
                fireAppointmentChanged();
            }
            return ResolvedPromise.VOID_PROMISE;
        }

        @SuppressWarnings("unchecked")
        public Promise<Void> undo()
        {
            Repeating repeating = appointment.getRepeating();
            if (add)
            {
                exceptions.forEach( (exception)-> repeating.removeException( exception.getStart()));
                updateExcpetionEditor(repeating);
                fireAppointmentChanged();
            }
            else
            {
                for (int i = 0; i < removedExceptions.length; i++)
                {
                    repeating.addException((LocalDateTime) removedExceptions[i]);
                }
                updateExcpetionEditor(repeating);
                fireAppointmentChanged();
            }
            return ResolvedPromise.VOID_PROMISE;
        }

        public String getCommandoName()
        {
            return getString("change") + " " + getString("appointment");
        }
    }

    protected void updateExcpetionEditor(Repeating repeating)
    {
        if (repeatingEditor == null) {
            return;
        }
        if ( repeatingEditor.exceptionEditor != null) {
            repeatingEditor.exceptionEditor.specialExceptions.setListData(repeating.getExceptions());
        }
    }

    private class ListRenderer extends DefaultListCellRenderer
    {
        private static final long serialVersionUID = 1L;

        public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
        {
            if (value != null)
            {
                setText(getString(value.toString()));
            }
            return this;
        }
    }

    public RepeatingType getCurrentRepeatingType()
    {
        if (noRepeating.isSelected())
        {
            return null;
        }

        RepeatingType repeatingType;
        if (monthlyRepeating.isSelected())
        {
            repeatingType = RepeatingType.MONTHLY;
        }
        else if (yearlyRepeating.isSelected())
        {
            repeatingType = RepeatingType.YEARLY;
        }
        else if (dailyRepeating.isSelected())
        {
            repeatingType = RepeatingType.DAILY;
        }
        else
        {
            repeatingType = RepeatingType.WEEKLY;
        }
        return repeatingType;
    }

    public Repeating getRepeating()
    {
        if (noRepeating.isSelected())
        {
            return null;
        }
        return repeating;
    }

    /**
     * This class collects any information of changes done to the radiobuttons
     * with which the repeating-type is selected.
     * This is where undo/redo for the changes of the radiobuttons, with which the repeating-type of an appointment
     * can be set, at the right of the edit view is realized.
     * @author Jens Fritz
     *
     */

    //Erstellt von Dominik Krickl-Vorreiter    
    public class UndoRepeatingTypeChange implements CommandUndo<RuntimeException>
    {

        private final RepeatingType oldRepeatingType;
        private final RepeatingType newRepeatingType;

        public UndoRepeatingTypeChange(RepeatingType oldRepeatingType, RepeatingType newRepeatingType)
        {
            this.oldRepeatingType = oldRepeatingType;
            this.newRepeatingType = newRepeatingType;
        }

        public Promise<Void> execute()
        {
            setRepeatingType(newRepeatingType);
            return ResolvedPromise.VOID_PROMISE;
        }

        public Promise<Void> undo()
        {
            setRepeatingType(oldRepeatingType);
            return ResolvedPromise.VOID_PROMISE;
        }

        private void setRepeatingType(RepeatingType repeatingType)
        {
            RepeatingRuleProjector.RepeatingChoice choice = RepeatingRuleProjector.choiceFor(repeatingType);
            selectRepeatingChoiceButton(choice);
            if (repeatingType == null)
            {
                repeatingCard.show(repeatingContainer, "0");
                singleEditor.mapFromAppointment();
                appointment.setRepeatingEnabled(false);
            }
            else
            {
                ReservationHelper.makeRepeatingForPeriod(getPeriodModel(), appointment, repeatingType, 1);
                repeatingEditor.mapFromAppointment();
                repeatingCard.show(repeatingContainer, "1");
            }

            savedRepeatingType = repeatingType;

            fireAppointmentChanged();
        }

        public String getCommandoName()
        {
            return getString("change") + " " + getString("repeating");
        }
    }

    public void nextFreeAppointment()
    {
        Reservation reservation = appointment.getReservation();
        Allocatable[] allocatables = reservation.getAllocatablesFor(appointment).toArray(Allocatable[]::new);
        PopupContext popupContext = dialogUiFactory.createPopupContext( this);
        try
        {
            CalendarOptions options = getCalendarOptions();
            Promise<LocalDateTime> nextAllocatableDate = getQuery().getNextAllocatableDate(Arrays.asList(allocatables), appointment, options);
            nextAllocatableDate.thenAccept((newStart) ->
            {
                if (newStart != null)
                {
                    Appointment oldState = ((AppointmentImpl) appointment).clone();
                    appointment.moveTo(newStart);
                    Appointment newState = ((AppointmentImpl) appointment).clone();
                    UndoDataChange changeDataCommand = new UndoDataChange(oldState, newState);
                    commandHistory.storeAndExecute(changeDataCommand);
                }
                else
                {
                    dialogUiFactory.showWarning("No free appointment found",popupContext);
                }
            });
        }
        catch (Exception ex)
        {
            dialogUiFactory.showException(ex, popupContext);
        }
    }

    public class UndoDataChange implements CommandUndo<RuntimeException>
    {

        private final Appointment oldState;
        private final Appointment newState;

        public UndoDataChange(Appointment oldState, Appointment newState)
        {
            this.oldState = oldState;
            this.newState = newState;
        }

        public Promise<Void> execute()
        {
            ((AppointmentImpl) appointment).copy(newState);
            if (appointment.isRepeatingEnabled())
            {
                repeatingEditor.mapFromAppointment();
            }
            else
            {
                singleEditor.mapFromAppointment();
            }
            fireAppointmentChanged();
            return ResolvedPromise.VOID_PROMISE;
        }

        public Promise<Void> undo()
        {
            ((AppointmentImpl) appointment).copy(oldState);
            if (appointment.isRepeatingEnabled())
            {
                repeatingEditor.mapFromAppointment();
            }
            else
            {
                singleEditor.mapFromAppointment();
            }
            fireAppointmentChanged();
            return ResolvedPromise.VOID_PROMISE;
        }

        public String getCommandoName()
        {
            return getString("change") + " " + getString("appointment");
        }

    }

    @SuppressWarnings("unchecked")
    private void setRenderer(JComboBox cb, ListCellRenderer listRenderer)
    {
        cb.setRenderer(listRenderer);
    }

    @SuppressWarnings("unchecked")
    private void setRenderer(JList cb, ListCellRenderer listRenderer)
    {
        cb.setCellRenderer(listRenderer);
    }
}
