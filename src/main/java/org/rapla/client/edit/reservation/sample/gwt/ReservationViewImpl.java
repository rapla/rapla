package org.rapla.client.edit.reservation.sample.gwt;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import org.rapla.client.base.AbstractView;
import org.rapla.client.edit.reservation.sample.ReservationView;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.edit.reservation.sample.gwt.subviews.ButtonsBar;
import org.rapla.client.edit.reservation.sample.gwt.subviews.InfoView;
import org.rapla.client.edit.reservation.sample.gwt.subviews.ResourceDatesView;
import org.rapla.client.gwt.components.InputUtils;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.entities.User;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;

import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PopupPanel.AnimationType;
import com.google.gwt.user.client.ui.TabBar;

public class ReservationViewImpl extends AbstractView<Presenter> implements ReservationView<IsWidget>
{

    private final Logger logger;

    private final FlowPanel content = new FlowPanel();
    private final FlowPanel tabBarPanel;
    private final ButtonsBar buttonsPanel;
    private final TabBar bar;
    private HandlerRegistration selectionHandler;
    private Reservation actuallShownReservation = null;
    private InfoView infoView;
    private ResourceDatesView resourcesView;

    private Map<String, PopupPanel> popups = new HashMap<String, PopupPanel>();

    @Inject
    public ReservationViewImpl(Logger logger)
    {
        super();
        this.logger = logger;
        content.setStyleName("content");
        tabBarPanel = new FlowPanel();
        tabBarPanel.setStyleName("tabbarWrapper");
        bar = new TabBar();
        bar.addStyleName("tabbar");
        bar.addTab("Veranstaltungsinformationen");
        bar.addTab("Termine und Ressourcen");
        bar.addTab("Rechte");
        tabBarPanel.add(bar);
        buttonsPanel = new ButtonsBar();
        bar.selectTab(0);
    }

    @Override
    public void setPresenter(org.rapla.client.edit.reservation.sample.ReservationView.Presenter presenter)
    {
        super.setPresenter(presenter);
        buttonsPanel.setPresenter(presenter);
    }

    @Override
    public void showWarning(String title, String warning)
    {
        RaplaPopups.showWarning(title, warning);
    }

    public void show(final Reservation reservation, final User user)
    {
        if (actuallShownReservation != null && actuallShownReservation.getId().equals(reservation.getId()))
        {
            // update existing
            final int selectedTab = bar.getSelectedTab();
            if (selectedTab == 0)
            {
                infoView.update(reservation);
            }
            else if (selectedTab == 1)
            {
                resourcesView.update(reservation);
            }
        }
        else
        {
            // kill the old one
            if (actuallShownReservation != null)
            {
                getPresenter().onCancelButtonClicked(actuallShownReservation);
            }
            actuallShownReservation = reservation;
            buttonsPanel.setReservation(reservation);
            // create new one
            final PopupPanel popup = RaplaPopups.createNewPopupPanel();
            popup.setAnimationEnabled(true);
            popup.setAnimationType(AnimationType.ROLL_DOWN);
            popups.put(reservation.getId(), popup);
            final RaplaLocale raplaLocale = getRaplaLocale();
            infoView = new InfoView(getPresenter(), raplaLocale, user);
            resourcesView = new ResourceDatesView(getPresenter(), raplaLocale);
            if (selectionHandler != null)
            {
                selectionHandler.removeHandler();
            }
            selectionHandler = bar.addSelectionHandler(new SelectionHandler<Integer>()
            {
                public void onSelection(SelectionEvent<Integer> event)
                {
                    final Integer index = event.getSelectedItem();
                    for (int i = 0; i < bar.getTabCount(); i++)
                    {
                        bar.setTabEnabled(i, true);
                    }
                    bar.setTabEnabled(index, false);
                    content.clear();
                    infoView.clearContent();
                    resourcesView.clearContent();
                    if (index == 0)
                    {
                        infoView.createContent(reservation);
                        content.add(infoView.provideContent());
                        final NodeList<Node> childNodes = content.getElement().getChildNodes();
                        InputUtils.focusOnFirstInput(childNodes);
                    }
                    else if (index == 1)
                    {
                        resourcesView.createContent(reservation);
                        content.add(resourcesView.provideContent());
                    }
                    else
                    {
                        RaplaPopups.showWarning("Navigation error", "Unknown View at index " + index);
                    }
                }
            });
            bar.selectTab(0);
            final FlowPanel layout = new FlowPanel();
            layout.add(buttonsPanel);
            layout.add(tabBarPanel);
            layout.add(content);
            popup.add(layout);
            popup.center();
            // find first input and focus on element
            final NodeList<Node> childNodes = content.getElement().getChildNodes();
            InputUtils.focusOnFirstInput(childNodes);
        }
    }

    public void mapFromReservation(Reservation event)
    {
        Locale locale = getRaplaLocale().getLocale();
        Allocatable[] resources = event.getAllocatables();
        {
            StringBuilder builder = new StringBuilder();
            for (Allocatable res : resources)
            {
                builder.append(res.getName(locale));
            }
        }
    }

    public void hide(final Reservation reservation)
    {
        actuallShownReservation = null;
        bar.selectTab(0, false);
        resourcesView = null;
        infoView = null;
        final PopupPanel popup = popups.get(reservation.getId());
        if (popup != null)
        {
            popup.hide();
        }
    }
}
