package org.rapla.client.edit.reservation.sample.gwt.subviews;

import org.gwtbootstrap3.client.ui.CheckBox;
import org.rapla.RaplaResources;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.edit.reservation.sample.gwt.ReservationViewImpl.ReservationViewPart;
import org.rapla.client.gwt.components.DateRangeComponent;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Reservation;

import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

public class ResourceDatesView implements ReservationViewPart
{

    private FlowPanel contentPanel;

    private Presenter presenter;

    private final RaplaResources i18n;

    private final BundleManager bundleManager;

    public ResourceDatesView(RaplaResources i18n, BundleManager bundleManager)
    {
        this.i18n = i18n;
        this.bundleManager = bundleManager;
        contentPanel = new FlowPanel();
        contentPanel.setStyleName("resourcesDates");
    }
    
    @Override
    public void setPresenter(Presenter presenter)
    {
        this.presenter = presenter;
    }

    protected Presenter getPresenter()
    {
        return presenter;
    }

    @Override
    public Widget provideContent()
    {
        return contentPanel;
    }

    @Override
    public void createContent(final Reservation reservation)
    {
        contentPanel.clear();
        final DateRangeComponent drp = new DateRangeComponent();
        contentPanel.add(drp);
        drp.setWithTime(true);
        final CheckBox checkBox = new CheckBox();
        checkBox.setHTML("all day");
        contentPanel.add(checkBox);
        checkBox.addChangeHandler(new ChangeHandler()
        {
            
            @Override
            public void onChange(ChangeEvent event)
            {
                Boolean value = checkBox.getValue();
                drp.setWithTime(!value);
            }
        });

//        final FlowPanel buttonBar = new FlowPanel();
//        buttonBar.setStyleName("datesButtonBar");
//
//        final Image buttonPlus = new Image(ImageImport.INSTANCE.plusIcon());
//        buttonPlus.setTitle(i18n.getString("new"));
//        buttonPlus.setStyleName("buttonsResourceDates");
//
//        buttonPlus.addClickHandler(new ClickHandler()
//        {
//            @Override
//            public void onClick(ClickEvent event)
//            {
//                //                getPresenter().onButtonPlusClicked();
//            }
//        });
//
//        final Image buttonGarbageCan = new Image(ImageImport.INSTANCE.crossGreyIcon());
//        buttonGarbageCan.setStyleName("buttonsResourceDates");
//        buttonGarbageCan.setTitle(i18n.getString("clear"));
//        buttonGarbageCan.addClickHandler(new ClickHandler()
//        {
//            public void onClick(ClickEvent e)
//            {
//                //                getPresenter().onGarbageCanButtonClicked();
//            }
//        });
//        final Image buttonNextGap = new Image(ImageImport.INSTANCE.nextGreyIcon());
//        buttonNextGap.setStyleName("buttonsResourceDates");
//
//        //
//        buttonBar.add(buttonPlus);
//        buttonBar.add(buttonGarbageCan);
//        buttonBar.add(buttonNextGap);
//
//        final FlowPanel dateInfos = new FlowPanel();
//        dateInfos.setStyleName("dateInfos");
//
//        final Date startDate = new Date();
//        DateValueChanged startChangeHandler = new DateValueChanged()
//        {
//            @Override
//            public void valueChanged(Date newValue)
//            {
//                final DateWithoutTimezone date = DateTools.toDate(newValue.getTime());
//                final TimeWithoutTimezone time = DateTools.toTime(newValue.getTime());
//                final Date dateTime = DateTools.toDateTime(newValue, newValue);
//                boolean equals =  dateTime.getTime() == newValue.getTime();
//            }
//        };
//        final DateTimeComponent begin = new DateTimeComponent(i18n.getString("start_date"), bundleManager, startDate, startChangeHandler);
//        {
//            //creatin the checkbox for whole day and add a handler
//            final CheckBox cbWholeDay = new CheckBox(i18n.getString("all-day"));
//            cbWholeDay.setStyleName("allDay");
//            begin.add(cbWholeDay);
//            cbWholeDay.addClickHandler(new ClickHandler()
//            {
//                @Override
//                public void onClick(ClickEvent event)
//                {
//                    //                getPresenter().onWholeDaySelected();
//                }
//
//            });
//        }
//        DateValueChanged endChangeHandler = new DateValueChanged()
//        {
//            @Override
//            public void valueChanged(Date newValue)
//            {
//                
//            }
//        };
//        final Date endDate = new Date();
//        // initialize and declarate Panel and Elements for End Time and Date
//        final DateTimeComponent end = new DateTimeComponent(i18n.getString("end_date"), bundleManager, endDate, endChangeHandler);
//
//        // Checkbox reccuring dates
//        final FlowPanel repeat = new FlowPanel();
//        repeat.setStyleName("repeating");
//        {// Repeating possibilities
//            final RadioButton daily = new RadioButton("repeat", i18n.getString("daily"));
//            daily.addClickHandler(new RepeatClickHandler());
//            final RadioButton weekly = new RadioButton("repeat", i18n.getString("weekly"));
//            weekly.addClickHandler(new RepeatClickHandler());
//            final RadioButton monthly = new RadioButton("repeat", i18n.getString("monthly"));
//            monthly.addClickHandler(new RepeatClickHandler());
//            final RadioButton year = new RadioButton("repeat", i18n.getString("yearly"));
//            year.addClickHandler(new RepeatClickHandler());
//            final RadioButton noReccuring = new RadioButton("repeat", i18n.getString("no_repeating"));
//            noReccuring.addClickHandler(new RepeatClickHandler());
//            repeat.add(noReccuring);
//            repeat.add(daily);
//            repeat.add(weekly);
//            repeat.add(monthly);
//            repeat.add(year);
//        }
//
//        //Setting for reccuring dates
//        final ListBox repeatType = new ListBox();
//        repeatType.addItem("Bis Datum");
//        repeatType.addItem("x Mal");
//
//        final Label repeatText = new Label("Beginn: ");
//        repeatText.setStyleName("beschriftung");
//
//
////        cbRepeatType.add(repeat);
//
//        //initializing the disclourePanel for the resources
//        final DisclosurePanel addResources = new DisclosurePanel("Ressourcen hinzuf\u00FCgen");
//        addResources.setStyleName("dateInfoLineComplete");
//
//        //load chosen resources
//        final FlowPanel chosenResources = new FlowPanel();
//        chosenResources.setStyleName("dateInfoLineComplete");
//
//        Label headerChosenRes = new Label("Ausgew\u00E4hlte Ressourcen:");
//        headerChosenRes.setStyleName("beschriftung");
//
//        chosenResources.setStyleName("dateInfoLineComplete");
//        chosenResources.add(headerChosenRes);
//
//        Label explainer2 = new Label("Es wurden bisher keine Ressourcen ausgew\u00E4hlt");
//        explainer2.setStyleName("wildcard");
//
//        FlowPanel chooseContainer = new FlowPanel();
//        chooseContainer.setStyleName("chooseContainer");
//
//        // Baumstruktur f\u00FCr verf\u00FCgbare Resourcen
//        final Tree resourceTree = new Tree();
//
//        // Filter
//        final Image filter = new Image(ImageImport.INSTANCE.filterIcon());
//        filter.setStyleName("buttonFilter");
//        filter.addClickHandler(new ClickHandler()
//        {
//            @Override
//            public void onClick(ClickEvent event)
//            {
//                //                getPresenter().onFilterClicked();
//            }
//
//        });
//
//        final ListBox filterEintr = new ListBox();
//        filterEintr.addItem("Verf\u00FCgbare Ressourcen");
//        filterEintr.addItem("Nicht Verf\u00FCgbare Ressourcen");
//        filterEintr.addItem("Kurse");
//        filterEintr.addItem("R\u00E4ume");
//        filterEintr.addItem("Professoren");
//        filterEintr.setStyleName("filterWindow");
//        filterEintr.setMultipleSelect(true);
//        filterEintr.setVisible(false);
//
//        // Suchfeld
//        final FlowPanel suche = new FlowPanel();
//        suche.setStyleName("suchfeld");
//        MultiWordSuggestOracle oracle = new MultiWordSuggestOracle();
//        oracle.add("WWI12B1");
//        oracle.add("K\u00FCstermann");
//        oracle.add("Daniel");
//        oracle.add("B343");
//
//        SuggestBox searchField = new SuggestBox(oracle);
//        searchField.setWidth("300px");
//        searchField.setStyleName("searchInput");
//
//        Image loupe = new Image(ImageImport.INSTANCE.loupeIcon());
//        loupe.setStyleName("buttonLoupe");
//
//        suche.add(searchField);
//        suche.add(loupe);
//        suche.add(filter);
//        suche.add(filterEintr);
//
//        chooseContainer.add(suche);
//        chooseContainer.add(resourceTree);
//        // chooseContainer.setWidth(width * 0.85 + "px");
//
//        addResources.setContent(chooseContainer);
//
//        final FlowPanel dateContentWrapper = new FlowPanel();
//        dateContentWrapper.setStyleName("dateContent");
//        dateContentWrapper.add(begin);
//        dateContentWrapper.add(end);
//        dateContentWrapper.add(repeat);
//        //        dateContentWrapper.add(addDateWithLabel);
//        dateInfos.add(dateContentWrapper);
//
//        // dateInfos.add(new HTML("<hr  style=\"width:90%;\" />"));
//        dateContentWrapper.add(chosenResources);
//        dateContentWrapper.add(addResources);
//
//        final TerminList dateList = new TerminList(new DateSelected()
//        {
//            @Override
//            public void selectDate(Appointment appointment)
//            {
//                begin.setDate(appointment.getStart());
//                end.setDate(appointment.getEnd());
//            }
//        }, reservation.getAppointments());
//        //The panel contains the Button to select all resources at the top of the datelist
//        FlowPanel placeholderSetResourcesToAll = new FlowPanel();
//        placeholderSetResourcesToAll.setStyleName("resourceButtonPanel");
//        final Button setResourcesToAll = new Button("Ressourcen f\u00FCr alle \u00FCbernehmen");
//        setResourcesToAll.setStyleName("resourceButton");
//        setResourcesToAll.setVisible(false);
//        placeholderSetResourcesToAll.add(setResourcesToAll);
//        dateList.add(placeholderSetResourcesToAll);
//        setResourcesToAll.addClickHandler(new ClickHandler()
//        {
//            @Override
//            public void onClick(ClickEvent event)
//            {
//                //                getPresenter().onSetResourcesToAllClicked();
//            }
//        });
//
//        contentPanel.add(dateList);
//        contentPanel.add(buttonBar);
//        contentPanel.add(dateInfos);
    }

    private class RepeatClickHandler implements ClickHandler
    {
        @Override
        public void onClick(ClickEvent event)
        {
            //            getPresenter().onrepeatTypeClicked(event);
        }
    }

    public void clearContent()
    {
        contentPanel.clear();
    }

    public void update(Reservation reservation)
    {
        createContent(reservation);
    }
}
