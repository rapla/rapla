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
package org.rapla.gui.internal.edit.reservation;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

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

import org.rapla.components.calendar.DateChangeEvent;
import org.rapla.components.calendar.DateChangeListener;
import org.rapla.components.calendar.RaplaCalendar;
import org.rapla.components.calendar.RaplaNumber;
import org.rapla.components.calendar.RaplaTime;
import org.rapla.components.layout.TableLayout;
import org.rapla.components.util.DateTools;
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
import org.rapla.framework.Disposable;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.gui.RaplaGUIComponent;
import org.rapla.gui.internal.common.PeriodChooser;
import org.rapla.gui.toolkit.DialogUI;
import org.rapla.gui.toolkit.MonthChooser;
import org.rapla.gui.toolkit.RaplaButton;
import org.rapla.gui.toolkit.RaplaWidget;
import org.rapla.gui.toolkit.WeekdayChooser;
import org.rapla.rest.gwtjsonrpc.common.FutureResult;

/** GUI for editing a single Appointment. */
public class AppointmentController extends RaplaGUIComponent
    implements
		 Disposable
		,RaplaWidget
{ 
	JPanel panel = new JPanel();

	SingleEditor singleEditor = new SingleEditor();

	JPanel repeatingContainer = new JPanel();
	RepeatingEditor repeatingEditor = new RepeatingEditor();

	Appointment appointment = null;
	Repeating repeating;
	
	RepeatingType savedRepeatingType = null;

	ArrayList<ChangeListener> listenerList = new ArrayList<ChangeListener>();
	JPanel repeatingType = new JPanel();
	JRadioButton noRepeating = new JRadioButton();
	JRadioButton weeklyRepeating = new JRadioButton();
	JRadioButton dailyRepeating = new JRadioButton();
	JRadioButton monthlyRepeating = new JRadioButton();
	JRadioButton yearlyRepeating = new JRadioButton();

	CardLayout repeatingCard = new CardLayout();
	// Button for splitting appointments
	RaplaButton convertButton = new RaplaButton();

	private CommandHistory commandHistory;
	
	Date selectedEditDate = null;

	public AppointmentController(RaplaContext sm, CommandHistory commandHistory)
			throws RaplaException {
		super(sm);
		this.commandHistory = commandHistory;
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

		panel.add(repeatingContainer, BorderLayout.CENTER);

		Border emptyLineBorder = new Border() {
			Insets insets = new Insets(1, 0, 0, 0);
			Color COLOR = Color.LIGHT_GRAY;

			public void paintBorder(Component c, Graphics g, int x, int y,
					int width, int height) {
				g.setColor(COLOR);
				g.drawLine(0, 0, c.getWidth(), 0);

			}

			public Insets getBorderInsets(Component c) {
				return insets;
			}

			public boolean isBorderOpaque() {
				return true;
			}

		};

		Border outerBorder = (BorderFactory.createCompoundBorder(
				BorderFactory.createEmptyBorder(0, 5, 0, 5)
				// ,BorderFactory.createEmptyBorder()
				, emptyLineBorder));

		repeatingContainer.setBorder(BorderFactory.createCompoundBorder( outerBorder, BorderFactory.createEmptyBorder(10, 5, 10, 5)));
		repeatingContainer.setLayout(repeatingCard);
		repeatingContainer.add(singleEditor.getComponent(), "0");
		repeatingContainer.add(repeatingEditor.getComponent(), "1");

		singleEditor.initialize();
		repeatingEditor.initialize();
		ActionListener listener = new ActionListener() {
			public void actionPerformed(ActionEvent evt) {
				switchRepeatings();
			}
		};
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

	private void switchRepeatings() {		
		UndoRepeatingTypeChange repeatingCommand = new UndoRepeatingTypeChange(savedRepeatingType, getCurrentRepeatingType());
		commandHistory.storeAndExecute(repeatingCommand);
	}

	public void setAppointment(Appointment appointment) {
		this.appointment = appointment;
		this.repeating = appointment.getRepeating();
		
		if (appointment.getRepeating() != null) {
			repeatingEditor.mapFromAppointment();
			repeatingCard.show(repeatingContainer, "1");
			if (repeating.isWeekly())
			{
				weeklyRepeating.setSelected(true);
			}
			else if (repeating.isDaily())
			{
				dailyRepeating.setSelected(true);
			}
			else if (repeating.isMonthly())
			{
				monthlyRepeating.setSelected(true);
			}
			else if (repeating.isYearly())
			{
				yearlyRepeating.setSelected(true);
			}
		} else {
			singleEditor.mapFromAppointment();
			repeatingCard.show(repeatingContainer, "0");
			noRepeating.setSelected(true);
		}
		
		savedRepeatingType = getCurrentRepeatingType();
	}

	public Appointment getAppointment() {
		return appointment;
	}
	
	public void setSelectedEditDate(Date selectedEditDate) {
	    this.selectedEditDate = selectedEditDate;
	}

	public Date getSelectedEditDate() {
        return selectedEditDate;
    }
	
	public void dispose() {
		singleEditor.dispose();
		repeatingEditor.dispose();
	}

	public JComponent getComponent() {
		return panel;
	}

	/**
	 * registers new ChangeListener for this component. An ChangeEvent will be
	 * fired to every registered ChangeListener when the appointment changes.
	 * 
	 * @see javax.swing.event.ChangeListener
	 * @see javax.swing.event.ChangeEvent
	 */
	public void addChangeListener(ChangeListener listener) {
		listenerList.add(listener);
	}

	/** removes a listener from this component. */
	public void removeChangeListener(ChangeListener listener) {
		listenerList.remove(listener);
	}

	public ChangeListener[] getChangeListeners() {
		return listenerList.toArray(new ChangeListener[] {});
	}

	protected void fireAppointmentChanged() {
		if (listenerList.size() == 0)
			return;
		ChangeEvent evt = new ChangeEvent(this);
		ChangeListener[] listeners = getChangeListeners();
		for (int i = 0; i < listeners.length; i++) {
			listeners[i].stateChanged(evt);
		}
		getLogger().debug("appointment changed: " + appointment);
	}

	static double ROW_SIZE = 21;

	private void setToWholeDays(final boolean oneDayEvent) {
		boolean wasSet = appointment.isWholeDaysSet();
		appointment.setWholeDays(oneDayEvent);
		if (wasSet && !oneDayEvent) 
		{
            // BJO 00000070
			CalendarOptions calenderOptions = getCalendarOptions();
			int startMinutes = calenderOptions.getWorktimeStartMinutes();
			int endMinutes = calenderOptions.getWorktimeEndMinutes();
			Date start = new Date(appointment.getStart().getTime() + startMinutes * DateTools.MILLISECONDS_PER_MINUTE);
			Date end = new Date(appointment.getEnd().getTime() - DateTools.MILLISECONDS_PER_DAY + endMinutes * DateTools.MILLISECONDS_PER_MINUTE);
            // BJO 00000070
			if ( end.before( start))
			{
				end =DateTools.addDay(end);
			}
			appointment.move(start, end);
		}
	}

	class SingleEditor implements DateChangeListener, Disposable {
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

		public SingleEditor() {
			double pre = TableLayout.PREFERRED;
			double size[][] = { { pre, 5, pre, 10, pre, 5, pre, 5, pre }, // Columns
					{ ROW_SIZE, 6, ROW_SIZE, 6, ROW_SIZE } }; // Rows
			TableLayout tableLayout = new TableLayout(size);
			content.setLayout(tableLayout);
		}

		public void initialize() {
			startDate = createRaplaCalendar();
			endDate = createRaplaCalendar();
			startTime = createRaplaTime();
			endTime = createRaplaTime();
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
			oneDayEventCheckBox.addItemListener(new ItemListener() {
				public void itemStateChanged(ItemEvent itemevent) {
					boolean selected = itemevent.getStateChange() == ItemEvent.SELECTED;
					setToWholeDays(selected);
					processChange(itemevent.getSource());
				}
			});

			startDate.addDateChangeListener(this);
			startTime.addDateChangeListener(this);
			endDate.addDateChangeListener(this);
			endTime.addDateChangeListener(this);
		}

		public JComponent getComponent() {
			return content;
		}

		public void dispose() {
		}

		public void dateChanged(DateChangeEvent evt) {
			final Object source = evt.getSource();
			processChange(source);
		}

		private void processChange(final Object source) {
			if (!listenerEnabled)
				return;
			try {
				listenerEnabled = false;
				RaplaLocale raplaLocale = getRaplaLocale();
				Date appStart = appointment.getStart();
				Date appEnd = appointment.getEnd();
				long duration = appEnd.getTime() - appStart.getTime();
				boolean wholeDaysSet = appointment.isWholeDaysSet();
				boolean oneDayEventSelected = oneDayEventCheckBox.isSelected();
				if (source == startDate || source == startTime) {
					Date date = startDate.getDate();
					Date time = startTime.getTime();
					Date newStart = raplaLocale.toDate(date,	time);
					Date newEnd = new Date(newStart.getTime() + duration);
					if (newStart.equals(appStart)	&& newEnd.equals(appEnd))
						return;
					UndoSingleEditorChange command = new UndoSingleEditorChange(appStart, appEnd,wholeDaysSet, newStart, newEnd,oneDayEventSelected);
					commandHistory.storeAndExecute(command);

				}
				if (source == endTime) {
					Date newEnd = raplaLocale.toDate(endDate.getDate(),	endTime.getTime());
					if (appStart.after(newEnd)) 
					{
						newEnd = DateTools.addDay(newEnd);
					}
					if (newEnd.equals(appEnd))
						return;
					UndoSingleEditorChange command = new UndoSingleEditorChange(null, appEnd,wholeDaysSet, null, newEnd, oneDayEventSelected);
					commandHistory.storeAndExecute(command);
				}
				if (source == endDate) {
					Date newEnd = raplaLocale.toDate(DateTools.addDays(endDate.getDate(), oneDayEventSelected ? 1	: 0), endTime.getTime());
					Date newStart = null;
					if (appStart.after(newEnd) || (oneDayEventSelected && !appStart.before( newEnd))) {
						long mod = duration % DateTools.MILLISECONDS_PER_DAY;
						if ( mod != 0)
						{
							newStart = new Date(newEnd.getTime()- mod);
						}
						else
						{
							newStart = DateTools.addDays(newEnd,-1);
						}
					}
					UndoSingleEditorChange command = new UndoSingleEditorChange(appStart, appEnd,wholeDaysSet, newStart, newEnd,oneDayEventSelected);
					commandHistory.storeAndExecute(command);
				}
				if (source == oneDayEventCheckBox) {
					Date date = startDate.getDate();
					Date time = startTime.getTime();
					Date oldStart = raplaLocale.toDate(date,	time);
					Date oldEnd = raplaLocale.toDate(endDate.getDate(),	endTime.getTime());
					UndoSingleEditorChange command = new UndoSingleEditorChange(oldStart, oldEnd,!oneDayEventSelected,appStart, appEnd,oneDayEventSelected);
					commandHistory.storeAndExecute(command);

				}
			} finally {
				listenerEnabled = true;
			}
		}

		private void mapFromAppointment() {
			listenerEnabled = false;
			try {
				final boolean wholeDaysSet = appointment.isWholeDaysSet();
				Date start = appointment.getStart();
				startDate.setDate(start);
				Date end = appointment.getEnd();
				endDate.setDate(DateTools.addDays(end,wholeDaysSet ? -1 : 0));
				endTime.setDurationStart( DateTools.isSameDay( start, end) ? start: null);
				startTime.setTime(start);
				endTime.setTime(end);
				oneDayEventCheckBox.setSelected(wholeDaysSet);
				startTimeLabel.setVisible(!wholeDaysSet);
				startTime.setVisible(!wholeDaysSet);
				endTime.setVisible(!wholeDaysSet);
				endTimeLabel.setVisible(!wholeDaysSet);
			} finally {
				listenerEnabled = true;
			}
			convertButton.setEnabled(false);
		}

		private void mapToAppointment() {
			RaplaLocale raplaLocale = getRaplaLocale();
			Date start = raplaLocale.toDate(startDate.getDate(),
					startTime.getTime());
			Date end = raplaLocale.toDate(endDate.getDate(), endTime.getTime());
			if (oneDayEventCheckBox.isSelected()) {
				end = raplaLocale.toDate(DateTools.addDay(endDate.getDate()),
						endTime.getTime());
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
		public class UndoSingleEditorChange implements CommandUndo<RuntimeException> {
			Date oldStart;
			Date oldEnd;
			boolean oldoneDay;

			Date newStart;
			Date newEnd;
			boolean newoneDay;

			public UndoSingleEditorChange(Date oldstart, Date oldend,
					boolean oldoneDay, Date newstart, Date newend,
					boolean newoneDay) {
				this.oldStart = oldstart;
				this.oldEnd = oldend;
				this.oldoneDay = oldoneDay;

				this.newStart = newstart;
				this.newEnd = newend;
				this.newoneDay = newoneDay;
			}

			public boolean execute()  {
				listenerEnabled = false;
				if (newStart != null && oldStart != null) {
					startTime.setTime(newStart);
					startDate.setDate(newStart);
					getLogger().debug("Starttime/-date adjusted");
				}
				if (newEnd != null && oldEnd != null) {
					endTime.setTime(newEnd);
					endDate.setDate(DateTools.addDays(newEnd, newoneDay ? -1
							: 0));
					getLogger().debug("Endtime/-date adjusted");
				}
				if (oldoneDay != newoneDay) {
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
				return true;
			}

			public boolean undo()  {
				listenerEnabled = false;
				if (oldStart != null && newStart != null) {
					startTime.setTime(oldStart);
					startDate.setDate(oldStart);
					getLogger().debug("Starttime/-date undo");
				}
				if (oldEnd != null && newEnd != null) {
					endTime.setTime(oldEnd);
					endDate.setDate(oldEnd);
					getLogger().debug("Endtime/-date undo");
				}
				if (oldoneDay != newoneDay) {
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
				return true;
			}

			public String getCommandoName()
			{
				return getString("change")+ " " + getString("appointment");
			}

		}
	}

	class RepeatingEditor implements ActionListener, DateChangeListener,
			ChangeListener, Disposable {
		JPanel content = new JPanel();

		JPanel intervalPanel = new JPanel();
		JPanel weekdayInMonthPanel = new JPanel();
		JPanel dayInMonthPanel = new JPanel();

		RaplaNumber interval = new RaplaNumber(null, RaplaNumber.ONE, null,	false);
		{
			addCopyPaste( interval.getNumberField());
		}
		RaplaNumber weekdayInMonth = new RaplaNumber(null, RaplaNumber.ONE,	new Integer(5), false);
		{
			addCopyPaste( weekdayInMonth.getNumberField());
		}
		RaplaNumber dayInMonth = new RaplaNumber(null, RaplaNumber.ONE,	new Integer(31), false);
		{
			addCopyPaste( dayInMonth.getNumberField());
		}

		WeekdayChooser weekdayChooser = new WeekdayChooser();
		JLabel dayLabel = new JLabel();

		JLabel startTimeLabel = new JLabel();
		RaplaTime startTime;
		JCheckBox oneDayEventCheckBox = new JCheckBox();

		JLabel endTimeLabel = new JLabel();
		JPanel endTimePanel = new JPanel();
		RaplaTime endTime;
		public final int SAME_DAY = 0, NEXT_DAY = 1, X_DAYS = 2;
		JComboBox dayChooser;
		RaplaNumber days = new RaplaNumber(null, new Integer(2), null, false);
		{
			addCopyPaste( days.getNumberField());
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
			addCopyPaste( number.getNumberField());
		}
		JPanel endDatePeriodPanel = new JPanel();
		PeriodChooser endDatePeriod;

		RaplaButton exceptionButton = new RaplaButton();
		ExceptionEditor exceptionEditor;
		DialogUI exceptionDlg;
		MonthChooser monthChooser = new MonthChooser();
		private boolean listenerEnabled = true;

		public RepeatingEditor() throws RaplaException {
			startDatePeriod = new PeriodChooser(getContext(), PeriodChooser.START_ONLY);
			endDatePeriod = new PeriodChooser(getContext(),	PeriodChooser.END_ONLY);
			// Create a TableLayout for the frame
			double pre = TableLayout.PREFERRED;
			double fill = TableLayout.FILL;
			double size[][] = {
					{ pre, 5, pre, 5, fill }, // Columns
					{ ROW_SIZE, 18, ROW_SIZE, 5, ROW_SIZE, 15, ROW_SIZE, 6,
							ROW_SIZE, 0 } }; // Rows
			TableLayout tableLayout = new TableLayout(size);
			content.setLayout(tableLayout);
		}

		public Locale getLocale() {
			return getI18n().getLocale();
		}

		public JComponent getComponent() {
			return content;
		}

		public void initialize() {
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
			weekdayInMonthPanel.add(new JLabel(	getString("repeating.interval.post")));

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
			startTime = createRaplaTime();
			startTime.addDateChangeListener(this);
			oneDayEventCheckBox.setText(getString("all-day"));

			oneDayEventCheckBox.addActionListener(this);
			// EndTime duration
			endTimeLabel.setText(getString("end_time"));
			endTime = createRaplaTime();
			endTime.addDateChangeListener(this);
			@SuppressWarnings("unchecked")
			JComboBox jComboBox = new JComboBox(new String[] {
					getString("appointment.same_day"),
					getString("appointment.next_day"),
					getString("appointment.day_x") });
			dayChooser = jComboBox;
			dayChooser.addActionListener(this);
			days.setColumns(2);
			endTimePanel.setLayout(new TableLayout(new double[][] {
					{ TableLayout.PREFERRED, 5, TableLayout.PREFERRED,
							TableLayout.FILL }, { ROW_SIZE } }));
			// endTimePanel.add(endTime,"0,0,l,f");
			endTimePanel.add(dayChooser, "0,0");
			endTimePanel.add(days, "2,0");
			days.setVisible(false);
			days.addChangeListener(this);

			// start-date (with period-box)
			startDatePeriod.addActionListener(this);
			startDateLabel.setText(getString("repeating.start_date"));
			startDate = createRaplaCalendar();
			startDate.addDateChangeListener(this);

			// end-date (with period-box)/n-times/forever
			endDatePeriod.addActionListener(this);
			endDate = createRaplaCalendar();
			endDate.addDateChangeListener(this);

			@SuppressWarnings("unchecked")
			JComboBox jComboBox2 = new JComboBox(new RepeatingEnding[] {
					RepeatingEnding.END_DATE, RepeatingEnding.N_TIMES,
					RepeatingEnding.FOREVEVER });
			endingChooser = jComboBox2;
			endingChooser.addActionListener(this);

			number.setColumns(3);
			number.setNumber(new Integer(1));
			number.addChangeListener(this);
			numberPanel.setLayout(new BorderLayout());
			numberPanel.add(number, BorderLayout.WEST);
			numberPanel.setVisible(false);
			intervalPanel.setVisible(false);
			weekdayInMonthPanel.setVisible(false);
			dayInMonthPanel.setVisible(false);

			// exception
			exceptionButton.setText(getString("appointment.exceptions")	+ " (0)");
			exceptionButton.addActionListener(this);

			content.add(intervalPanel, "0,0,l,f");
			content.add(weekdayInMonthPanel, "0,0,l,f");
			content.add(dayInMonthPanel, "0,0,l,f");
			content.add(weekdayChooser, "2,0,f,f");
			content.add(monthChooser, "2,0,f,f");
			content.add(dayLabel, "2,0,l,f");

			content.add(startTimeLabel, "0,2,l,f");
			content.add(startTime, "2,2,f,f");
			content.add(oneDayEventCheckBox, "4,2");

			content.add(exceptionButton, "4,0,r,t");

			content.add(endTimeLabel, "0,4,l,f");
			content.add(endTime, "2,4,f,f");
			content.add(endTimePanel, "4,4,4,4,l,f");

			content.add(startDateLabel, "0,6,l,f");
			content.add(startDate, "2,6,l,f");
			content.add(startDatePeriod, "4,6,f,f");

			content.add(endingChooser, "0,8,l,f");
			content.add(endDate, "2,8,l,f");
			content.add(endDatePeriodPanel, "4,8,f,f");
			// We must surround the endDatePeriod with a panel to
			// separate visiblity of periods from visibility of the panel
			endDatePeriodPanel.setLayout(new BorderLayout());
			endDatePeriodPanel.add(endDatePeriod, BorderLayout.CENTER);
			content.add(numberPanel, "2,8,f,f");
			
			setRenderer(endingChooser,new ListRenderer());
			// Rapla 1.4: Initialize the split appointment button
			convertButton.addActionListener(this);

			// content.add(exceptionLabel,"0,10,l,c");
			// content.add(exceptionPanel,"2,10,4,10,l,c");
		}



		public void dispose() {
			endDatePeriod.removeActionListener(this);
			startDatePeriod.removeActionListener(this);
		}

		private Date getStart() {
			Date start = getRaplaLocale().toDate(startDate.getDate(), startTime.getTime());
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

		private Date getEnd() {
			Date end = getRaplaLocale().toDate(getStart(), endTime.getTime());
			if (dayChooser.getSelectedIndex() == NEXT_DAY)
				end = DateTools.addDay(end);
			if (dayChooser.getSelectedIndex() == X_DAYS)
				end = DateTools.addDays(end, days.getNumber().intValue());
			return end;
		}

		public void actionPerformed(ActionEvent evt) {
			if (evt.getSource() == exceptionButton) {
				try {
					showExceptionDlg();
				} catch (RaplaException ex) {
					showException(ex, content);
				}
				return;
			}

			if (!listenerEnabled)
				return;
			try {
				listenerEnabled = false;

				// Rapla 1.4: Split appointment button has been clicked
				if (evt.getSource() == convertButton) {
					// Notify registered listeners
					ActionListener[] listeners = listenerList.toArray(new ActionListener[] {});
					for (int i = 0; i < listeners.length; i++) {
						listeners[i].actionPerformed(new ActionEvent(AppointmentController.this,ActionEvent.ACTION_PERFORMED, "split"));
					}
					return;
				}
				else if (evt.getSource() == endingChooser) {
					// repeating has changed to UNTIL, the default endDate will
					// be set
					int index = endingChooser.getSelectedIndex();
					if (index == REPEAT_UNTIL) {
						Date slotDate = getSelectedEditDate();
						if (slotDate != null)
							endDate.setDate(slotDate);
					}
				} else if (evt.getSource() == weekdayChooser) {
					Calendar calendar = getRaplaLocale().createCalendar();
					calendar.setTime(startDate.getDate());
					calendar.set(Calendar.DAY_OF_WEEK, weekdayChooser.getSelectedWeekday());
					startDate.setDate(calendar.getTime());
				} else if (evt.getSource() == monthChooser) {
					Calendar calendar = getRaplaLocale().createCalendar();
					calendar.setTime(startDate.getDate());
					calendar.set(Calendar.MONTH, monthChooser.getSelectedMonth());
					calendar.set(Calendar.DAY_OF_MONTH, dayInMonth.getNumber().intValue());
					startDate.setDate(calendar.getTime());
				} else if (evt.getSource() == dayChooser) {
					if (dayChooser.getSelectedIndex() == SAME_DAY) {
						if (getEnd().before(getStart())) {
							endTime.setTime(getStart());
							getLogger().debug("endtime adjusted");
						}
					}
				} else if (evt.getSource() == startDatePeriod && startDatePeriod.getPeriod() != null) {
					Calendar calendar = getRaplaLocale().createCalendar();
					calendar.setTime(startDatePeriod.getPeriod().getStart());
					if (repeating.isWeekly() || repeating.isMonthly()) {
						calendar.set(Calendar.DAY_OF_WEEK, weekdayChooser.getSelectedWeekday());
						if (calendar.getTime().before( startDatePeriod.getPeriod().getStart())) {
							calendar.add(Calendar.DAY_OF_WEEK, 7);
						}
					}
					getLogger().debug("startdate adjusted to period");
					startDate.setDate(calendar.getTime());
					endDatePeriod.setSelectedPeriod(startDatePeriod.getPeriod());
				} else if (evt.getSource() == endDatePeriod	&& endDatePeriod.getDate() != null) {
					endDate.setDate(DateTools.subDay(endDatePeriod.getDate()));
					getLogger().debug("enddate adjusted to period");
				}

				doChanges();
			} finally {
				listenerEnabled = true;
			}
		}

		public void stateChanged(ChangeEvent evt) {
			if (!listenerEnabled)
				return;
			try {
				listenerEnabled = false;

				if (evt.getSource() == weekdayInMonth && repeating.isMonthly()) {
					Number weekdayOfMonthValue = weekdayInMonth.getNumber();
					if (weekdayOfMonthValue != null && repeating.isMonthly()) {
						Calendar cal = getRaplaLocale().createCalendar();
						cal.setTime(appointment.getStart());
						cal.set(Calendar.DAY_OF_WEEK_IN_MONTH,	weekdayOfMonthValue.intValue());
						startDate.setDate(cal.getTime());
					}
				}
				if (evt.getSource() == dayInMonth && repeating.isYearly()) {
					Number dayOfMonthValue = dayInMonth.getNumber();
					if (dayOfMonthValue != null && repeating.isYearly()) {
						Calendar cal = getRaplaLocale().createCalendar();
						cal.setTime(appointment.getStart());
						cal.set(Calendar.DAY_OF_MONTH,	dayOfMonthValue.intValue());
						startDate.setDate(cal.getTime());
					}
				}

				doChanges();
			} finally {
				listenerEnabled = true;
			}
		}

		public void dateChanged(DateChangeEvent evt) {
			if (!listenerEnabled)
				return;
			try {
				listenerEnabled = false;

				long duration = appointment.getEnd().getTime()- appointment.getStart().getTime();
				if (evt.getSource() == startTime) {
					Date newEnd = new Date(getStart().getTime() + duration);
					endTime.setTime(newEnd);
					getLogger().debug("endtime adjusted");
				}

				if (evt.getSource() == endTime) {
					Date newEnd = getEnd();
					if (getStart().after(newEnd)) {
						newEnd = DateTools.addDay(newEnd);
						endTime.setTime(newEnd);
						getLogger().debug("enddate adjusted");
					}
				}
				
				doChanges();
			} finally {
				listenerEnabled = true;
			}
		}

		private void mapToAppointment() {
			int index = endingChooser.getSelectedIndex();
			Number intervalValue = interval.getNumber();

			if (intervalValue != null)
			{
				repeating.setInterval(intervalValue.intValue());
			}
			else
			{
				repeating.setInterval(1);
			}
			
			if (index == REPEAT_UNTIL) {
				if (DateTools.countDays(startDate.getDate(), endDate.getDate()) < 0)
				{
					endDate.setDate(startDate.getDate());
				}
				repeating.setEnd(DateTools.addDay(endDate.getDate()));
			} else if (index == REPEAT_N_TIMES) {
				Number numberValue = number.getNumber();
				if (number != null)
				{
					repeating.setNumber(numberValue.intValue());
				}
				else
				{
					repeating.setNumber(1);
				}
			} else { // REPEAT_FOREVER
				repeating.setEnd(null);
				repeating.setNumber(-1);
			}
			appointment.move(getStart(), getEnd());
			// We have todo the after the move to avoid reseting the dates
			final boolean oneDayEvent = oneDayEventCheckBox.isSelected();
			setToWholeDays(oneDayEvent);
		}

		private void updateExceptionCount() {
			int count = repeating.getExceptions().length;
			if (count > 0) {
				exceptionButton.setForeground(Color.red);
			} else {
				exceptionButton.setForeground(UIManager.getColor("Label.foreground"));
			}
			String countValue = String.valueOf(count);
			if (count < 9) {
				countValue = " " + countValue + " ";
			}
			exceptionButton.setText(getString("appointment.exceptions") + " ("
					+ countValue + ")");
		}

		private void showEnding(int index) {
			if (index == REPEAT_UNTIL) {
				endDate.setVisible(true);
				endDatePeriodPanel.setVisible(isPeriodVisible());
				numberPanel.setVisible(false);
			}
			if (index == REPEAT_N_TIMES) {
				endDate.setVisible(false);
				endDatePeriodPanel.setVisible(false);
				numberPanel.setVisible(true);
			}
			if (index == REPEAT_FOREVER) {
				endDate.setVisible(false);
				endDatePeriodPanel.setVisible(false);
				numberPanel.setVisible(false);
			}
		}

		private void mapFromAppointment() {
			// closing is not necessary as dialog is modal
//			if (exceptionDlg != null && exceptionDlg.isVisible())
//				exceptionDlg.dispose();
			repeating = appointment.getRepeating();
			if (repeating == null) {
				return;
			}
			listenerEnabled = false;
			try {
				updateExceptionCount();
				if (exceptionEditor != null)
					exceptionEditor.mapFromAppointment();

				interval.setNumber(new Integer(repeating.getInterval()));

				Date start = appointment.getStart();
				startDate.setDate(start);
				startDatePeriod.setDate(start);
				startTime.setTime(start);
				Date end = appointment.getEnd();
				endTime.setTime(end);
				endTime.setDurationStart( DateTools.isSameDay( start, end) ? start: null);

				weekdayInMonthPanel.setVisible(repeating.isMonthly());
				intervalPanel.setVisible(repeating.isDaily() || repeating.isWeekly());
				dayInMonthPanel.setVisible(repeating.isYearly());

				if (repeating.getEnd() != null) {
					endDate.setDate(DateTools.subDay(repeating.getEnd()));
					endDatePeriod.setDate(DateTools.cutDate(endDate.getDate()));
					number.setNumber(new Integer(repeating.getNumber()));
					if (!repeating.isFixedNumber()) {
						endingChooser.setSelectedIndex(REPEAT_UNTIL);
						showEnding(REPEAT_UNTIL);
					} else {
						endingChooser.setSelectedIndex(REPEAT_N_TIMES);
						showEnding(REPEAT_N_TIMES);
					}
				} else {
					endingChooser.setSelectedIndex(REPEAT_FOREVER);
					showEnding(REPEAT_FOREVER);
				}

				startDatePeriod.setVisible(isPeriodVisible() && (repeating.isDaily() || repeating.isWeekly()));
				endDatePeriod.setVisible(repeating.isDaily() || repeating.isWeekly());
				if (repeating.isWeekly() || repeating.isMonthly()) {
					dayLabel.setVisible(false);
					weekdayChooser.setVisible(true);
					monthChooser.setVisible(false);
					Calendar calendar = getRaplaLocale().createCalendar();
					calendar.setTime(start);
					weekdayChooser.selectWeekday(calendar.get(Calendar.DAY_OF_WEEK));
				}

				if (repeating.isYearly()) {
					dayLabel.setVisible(false);
					weekdayChooser.setVisible(false);
					monthChooser.setVisible(true);
					Calendar cal = getRaplaLocale().createCalendar();
					cal.setTime(start);
					monthChooser.selectMonth(cal.get(Calendar.MONTH));
					int numb = cal.get(Calendar.DAY_OF_MONTH);
					dayInMonth.setNumber(new Integer(numb));
				}

				if (repeating.isMonthly()) {
					Calendar cal = getRaplaLocale().createCalendar();
					cal.setTime(start);
					int numb = cal.get(Calendar.DAY_OF_WEEK_IN_MONTH);
					weekdayInMonth.setNumber(new Integer(numb));
				}

				if (repeating.isDaily()) {
					dayLabel.setVisible(true);
					weekdayChooser.setVisible(false);
					monthChooser.setVisible(false);
				}

				String typeString = repeating.getType().toString();
				startDateLabel.setText(getString(typeString) + " " + getString("repeating.start_date"));

				int daysBetween = (int) DateTools.countDays(
						start, end);
				if (daysBetween == 0) {
					dayChooser.setSelectedIndex(SAME_DAY);
					days.setVisible(false);
				} else if (daysBetween == 1) {
					dayChooser.setSelectedIndex(NEXT_DAY);
					days.setVisible(false);
				} else {
					dayChooser.setSelectedIndex(X_DAYS);
					days.setNumber(new Integer(daysBetween));
					days.setVisible(true);
				}
				final boolean wholeDaysSet = appointment.isWholeDaysSet();
				startTime.setEnabled(!wholeDaysSet);
				endTime.setEnabled(!wholeDaysSet);
				dayChooser.setEnabled(!wholeDaysSet);
                days.setEnabled( !wholeDaysSet);
				oneDayEventCheckBox.setSelected(wholeDaysSet);
			} finally {
				listenerEnabled = true;
			}
			convertButton.setEnabled(repeating.getEnd() != null);
			getComponent().revalidate();
		}

		private boolean isPeriodVisible() {
			try {
				return getQuery().getPeriodModel().getSize() > 0;
			} catch (RaplaException e) {
				return false;
			}
		}

		private void showExceptionDlg() throws RaplaException {
			exceptionEditor = new ExceptionEditor();
			exceptionEditor.initialize();
			exceptionEditor.mapFromAppointment();
			exceptionEditor.getComponent().setBorder( BorderFactory.createEmptyBorder(5, 5, 5, 5));
			exceptionDlg = DialogUI.create(getContext(), getComponent(), true,
					exceptionEditor.getComponent(),
					new String[] { getString("close") });
			exceptionDlg.setTitle(getString("appointment.exceptions"));
			exceptionDlg.start();
			updateExceptionCount();
		}
	
		private void doChanges(){
			Appointment oldState = ((AppointmentImpl) appointment).clone();
			mapToAppointment();
			Appointment newState = ((AppointmentImpl) appointment).clone();
			UndoDataChange changeDataCommand = new UndoDataChange(oldState, newState);
			commandHistory.storeAndExecute(changeDataCommand);
		}
	}

	class ExceptionEditor implements ActionListener, ListSelectionListener {
		JPanel content = new JPanel();
		RaplaCalendar exceptionDate;
		RaplaButton addButton = new RaplaButton(RaplaButton.SMALL);
		RaplaButton removeButton = new RaplaButton(RaplaButton.SMALL);
		JList specialExceptions = new JList();

		public ExceptionEditor() {
			// Create a TableLayout for the frame
			double pre = TableLayout.PREFERRED;
			double min = TableLayout.MINIMUM;
			double fill = TableLayout.FILL;
			double yborder = 8;
			double size[][] = { { pre, pre, 0.1, 50, 100, 0.9 }, // Columns
					{ yborder, min, min, fill } }; // Rows
			TableLayout tableLayout = new TableLayout(size);
			content.setLayout(tableLayout);
		}

		public JComponent getComponent() {
			return content;
		}

		public void initialize() {
			addButton.setText(getString("add"));
			addButton.setIcon(getIcon("icon.arrow_right"));
			removeButton.setText(getString("remove"));
			removeButton.setIcon(getIcon("icon.arrow_left"));
			exceptionDate = createRaplaCalendar();
			/*
			 * this.add(new JLabel(getString("appointment.exception.general") +
			 * " "),"0,1"); this.add(new JScrollPane(generalExceptions
			 * ,JScrollPane.VERTICAL_SCROLLBAR_ALWAYS
			 * ,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) ,"1,1,1,3,t");
			 */
			JLabel label = new JLabel(getString("appointment.exception.days")
					+ " ");
			label.setHorizontalAlignment(SwingConstants.RIGHT);
			content.add(label, "3,1,4,1,r,t");
			content.add(exceptionDate, "5,1,l,t");
			content.add(addButton, "4,2,f,t");
			content.add(removeButton, "4,3,f,t");
			content.add(new JScrollPane(specialExceptions,
					JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
					JScrollPane.HORIZONTAL_SCROLLBAR_NEVER), "5,2,5,3,t");

			addButton.addActionListener(this);
			removeButton.addActionListener(this);
			specialExceptions.addListSelectionListener(this);
			removeButton.setEnabled(false);
			specialExceptions.setFixedCellWidth(200);
            specialExceptions.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
			DefaultListCellRenderer cellRenderer = new DefaultListCellRenderer() {
				private static final long serialVersionUID = 1L;

				public Component getListCellRendererComponent(JList list,
						Object value, int index, boolean isSelected,
						boolean cellHasFocus) {
					if (value instanceof Date)
						value = getRaplaLocale().formatDateLong((Date) value);
					return super.getListCellRendererComponent(list, value,	index, isSelected, cellHasFocus);
				}
			};
			setRenderer(specialExceptions, cellRenderer);
			specialExceptions.addMouseListener(new MouseAdapter() {
				public void mouseClicked(MouseEvent evt) {
					if (evt.getClickCount() > 1) {
						removeException();
					}
				}
			});
		}

		@SuppressWarnings("unchecked")
		public void mapFromAppointment() {
			if (appointment.getRepeating() == null)
				specialExceptions.setListData(new Object[0]);
			else
				specialExceptions.setListData(appointment.getRepeating().getExceptions());
			// exceptionDate.setDate( appointment.getStart());
			Date exceptDate = getSelectedEditDate();
			if (exceptDate == null)
				exceptionDate.setDate(appointment.getStart());
			else
				exceptionDate.setDate(exceptDate);
		}

		public void actionPerformed(ActionEvent evt) {
			if (evt.getSource() == addButton) {
				addException();
			}
			if (evt.getSource() == removeButton) {
				removeException();
			}
		}

		private void addException() {
			Date date = exceptionDate.getDate();
			if (appointment.getRepeating().isException(date.getTime()))
				return;
			UndoExceptionChange command = new UndoExceptionChange( addButton, date, null);
			commandHistory.storeAndExecute(command);
		}

		@SuppressWarnings("deprecation")
		private void removeException() {
			if (specialExceptions.getSelectedValues() == null)
				return;
			Object[] selectedExceptions = specialExceptions.getSelectedValues();
			UndoExceptionChange command = new UndoExceptionChange( removeButton, null, selectedExceptions);
			commandHistory.storeAndExecute(command);
		}

		public void valueChanged(ListSelectionEvent e) {
			if (e.getSource() == specialExceptions) {
				removeButton.setEnabled(specialExceptions.getSelectedValue() != null);
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
		
	    //Erstellt von Dominik Krickl-Vorreiter    
		public class UndoExceptionChange implements CommandUndo<RuntimeException> {
			RaplaButton pressedButton;
			Date exception;
			Object[] selectedExceptions;

			public UndoExceptionChange(RaplaButton pressedButton,
					Date exception, Object[] selectedExceptions) {
				this.pressedButton = pressedButton;
				this.exception = exception;
				this.selectedExceptions = selectedExceptions;
			}

			@SuppressWarnings("unchecked")
			public boolean execute()  {
				Repeating repeating = appointment.getRepeating();
				if (pressedButton == addButton) {
					repeating.addException(exception);
					specialExceptions.setListData(repeating.getExceptions());
					fireAppointmentChanged();
				}
				if (pressedButton == removeButton) {
					for (int i = 0; i < selectedExceptions.length; i++) {
						repeating.removeException((Date) selectedExceptions[i]);
					}
					specialExceptions.setListData(repeating.getExceptions());
					fireAppointmentChanged();
				}
				return true;
			}

			@SuppressWarnings("unchecked")
			public boolean undo() {
				Repeating repeating = appointment.getRepeating();
				if (pressedButton == addButton) {
					repeating.removeException(exception);
					specialExceptions.setListData(repeating.getExceptions());
					fireAppointmentChanged();
				}
				if (pressedButton == removeButton) {
					for (int i = 0; i < selectedExceptions.length; i++) {
						repeating.addException(
								(Date) selectedExceptions[i]);
					}
					specialExceptions.setListData(repeating.getExceptions());
					fireAppointmentChanged();
				}
				return true;
			}

			public String getCommandoName() {
				return getString("change")+ " " + getString("appointment");
			}
		}
	}

	private class ListRenderer extends DefaultListCellRenderer {
		private static final long serialVersionUID = 1L;

		public Component getListCellRendererComponent(JList list, Object value,
				int index, boolean isSelected, boolean cellHasFocus) {
			if (value != null) {
				setText(getString(value.toString()));
			}
			return this;
		}
	}

	public RepeatingType getCurrentRepeatingType() {
		if (noRepeating.isSelected()) {
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

	public Repeating getRepeating() {
		if (noRepeating.isSelected()) {
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
	public class UndoRepeatingTypeChange implements CommandUndo<RuntimeException> {

		private final RepeatingType oldRepeatingType;
		private final RepeatingType newRepeatingType;
		
		public UndoRepeatingTypeChange(RepeatingType oldRepeatingType, RepeatingType newRepeatingType) {
			this.oldRepeatingType = oldRepeatingType;
			this.newRepeatingType = newRepeatingType;
		}
		
		public boolean execute()  {
			setRepeatingType(newRepeatingType);
			return true;
		}

		public boolean undo()  {
			setRepeatingType(oldRepeatingType);
			return true;
		}
		
		private void setRepeatingType(RepeatingType repeatingType)  {
			if (repeatingType == null) 
			{
				noRepeating.setSelected(true);
				
				repeatingCard.show(repeatingContainer, "0");
				singleEditor.mapFromAppointment();
				appointment.setRepeatingEnabled(false);
			} 
			else 
			{
				if (repeatingType == RepeatingType.WEEKLY)
				{
					weeklyRepeating.setSelected(true);
				} 
				else if (repeatingType == RepeatingType.DAILY)
				{
					dailyRepeating.setSelected(true);
				}
				else if (repeatingType == RepeatingType.MONTHLY)
				{
					monthlyRepeating.setSelected(true);
				}
				else if (repeatingType == RepeatingType.YEARLY)
				{
					yearlyRepeating.setSelected(true);
				}
				ReservationHelper.makeRepeatingForPeriod(getPeriodModel(), appointment, repeatingType,1);
				repeatingEditor.mapFromAppointment();
				repeatingCard.show(repeatingContainer, "1");
			}
			
			savedRepeatingType = repeatingType;
			
			fireAppointmentChanged();
		}
		
		public String getCommandoName() {
			return getString("change")+ " " + getString("repeating");
		}
	}

	public void nextFreeAppointment() {
		Reservation reservation = appointment.getReservation();
		Allocatable[] allocatables = reservation.getAllocatablesFor( appointment);
		try
		{
			CalendarOptions options = getCalendarOptions();
			FutureResult<Date> nextAllocatableDate = getQuery().getNextAllocatableDate(Arrays.asList(allocatables), appointment,options );
            Date newStart = nextAllocatableDate.get();
			if ( newStart != null)
			{
				Appointment oldState = ((AppointmentImpl) appointment).clone();
				appointment.move(newStart);
				Appointment newState = ((AppointmentImpl) appointment).clone();
				UndoDataChange changeDataCommand = new UndoDataChange(oldState, newState);
				commandHistory.storeAndExecute(changeDataCommand);
			}
			else
			{
				showWarning("No free appointment found", getMainComponent());
			}
		} 
		catch (Exception ex)
		{
			showException( ex, getMainComponent());
		}
	}

	public class UndoDataChange implements CommandUndo<RuntimeException> {

		private final Appointment oldState;
		private final Appointment newState;
		
		public UndoDataChange(Appointment oldState, Appointment newState) {
			this.oldState = oldState;
			this.newState = newState;
		}
		
		public boolean execute() {
			((AppointmentImpl) appointment).copy(newState);
			if ( appointment.isRepeatingEnabled())
			{
				repeatingEditor.mapFromAppointment();
				
			}
			else
			{
				singleEditor.mapFromAppointment();
			}
			fireAppointmentChanged();
			return true;
		}

		public boolean undo()  {
			((AppointmentImpl) appointment).copy(oldState);
			if ( appointment.isRepeatingEnabled())
			{
				repeatingEditor.mapFromAppointment();
			}
			else
			{
				singleEditor.mapFromAppointment();
			}
			fireAppointmentChanged();
			return true;
		}
		
		public String getCommandoName() {
			return getString("change")+ " " + getString("appointment");
		}
		
	}


	@SuppressWarnings("unchecked")
	private void setRenderer(JComboBox cb,	ListCellRenderer listRenderer) {
		cb.setRenderer( listRenderer);
	}
	@SuppressWarnings("unchecked")
	private void setRenderer(JList cb,	ListCellRenderer listRenderer) {
		cb.setCellRenderer( listRenderer);
	}
}
