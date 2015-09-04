package org.rapla.client.edit.reservation.sample.gwt;

import java.util.ArrayList;
import java.util.Locale;

import javax.inject.Inject;

import org.gwtbootstrap3.client.ui.AnchorListItem;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.NavTabs;
import org.gwtbootstrap3.client.ui.constants.IconSize;
import org.gwtbootstrap3.client.ui.constants.IconType;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.RaplaResources;
import org.rapla.client.base.AbstractView;
import org.rapla.client.edit.reservation.sample.ReservationView;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.edit.reservation.sample.gwt.subviews.InfoView;
import org.rapla.client.edit.reservation.sample.gwt.subviews.ResourceDatesView;
import org.rapla.client.gwt.components.InputUtils;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Allocatable;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.logger.Logger;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.LIElement;
import com.google.gwt.dom.client.Node;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.PopupPanel.AnimationType;
import com.google.gwt.user.client.ui.Widget;

public class ReservationViewImpl extends AbstractView<Presenter>implements ReservationView<IsWidget>
{

    private final RaplaResources i18n;
    private final BundleManager bundleManager;

    private final Logger logger;

    private final Div content = new Div();
    private final Div buttons = new Div();
    private final NavTabs bar = new NavTabs();
    private Reservation actuallShownReservation = null;
    private PopupPanel popup;
    final ArrayList<Dual> navEntries = new ArrayList<Dual>();

    private static class Dual
    {
        private final AnchorListItem menuItem;
        private final ReservationViewPart view;

        public Dual(AnchorListItem menuItem, ReservationViewPart view)
        {
            this.menuItem = menuItem;
            this.view = view;
        }

        public AnchorListItem getMenuItem()
        {
            return menuItem;
        }

        public ReservationViewPart getView()
        {
            return view;
        }
    }

    public interface ReservationViewPart
    {

        void setPresenter(Presenter presenter);

        Widget provideContent();

        void createContent(Reservation reservation);

    }

    @Inject
    public ReservationViewImpl(Logger logger, RaplaResources i18n, BundleManager  bundleManager)
    {
        super();
        this.i18n = i18n;
        this.logger = logger;
        this.bundleManager = bundleManager;
        content.setStyleName("content");
        navEntries.add(new Dual(new AnchorListItem("Veranstaltungsinformationen"), new InfoView(i18n, bundleManager)));
        navEntries.add(new Dual(new AnchorListItem("Termine und Ressourcen"), new ResourceDatesView(i18n, bundleManager)));
        navEntries.add(new Dual(new AnchorListItem("Rechte"), null));
        for (Dual dual : navEntries)
        {
            AnchorListItem menuItem = dual.getMenuItem();
            bar.add(menuItem);
        }
        {
            IconType type = IconType.SAVE;
            ClickHandler handler = new ClickHandler()
            {
                @Override
                public void onClick(ClickEvent event)
                {
                    getPresenter().onSaveButtonClicked(actuallShownReservation);
                }
            };
            createIcon(type, handler, "Save");
        }
        {
            IconType type = IconType.REMOVE;
            ClickHandler handler = new ClickHandler()
            {
                @Override
                public void onClick(ClickEvent event)
                {
                    getPresenter().onCancelButtonClicked(actuallShownReservation);
                }
            };
            createIcon(type, handler, "Cancel");
        }
        {
            IconType type = IconType.TRASH;
            ClickHandler handler = new ClickHandler()
            {
                @Override
                public void onClick(ClickEvent event)
                {
                    getPresenter().onDeleteButtonClicked(actuallShownReservation);
                }
            };
            createIcon(type, handler, "Delete");
        }
        bar.addDomHandler(new ClickHandler()
        {
            @Override
            public void onClick(ClickEvent event)
            {
                Element relativeElement = DOM.eventGetTarget(com.google.gwt.user.client.Event.as(event.getNativeEvent()));
                while (relativeElement != null && !(LIElement.is(relativeElement)))
                {
                    relativeElement = relativeElement.getParentElement();
                }
                if (relativeElement == null)
                {
                    return;
                }
                content.clear();
                activate(relativeElement);
            }

        }, ClickEvent.getType());
    }

    public void createIcon(IconType type, ClickHandler handler, String text)
    {
        Button button = new Button(text);
        button.setIcon(type);
        button.setIconSize(IconSize.TIMES2);
        button.addClickHandler(handler);
        buttons.add(button);
    }

    private void activate(Element relativeElement)
    {
        for (Dual navEntry : navEntries)
        {
            AnchorListItem menuItem = navEntry.getMenuItem();
            Element element = menuItem.getElement();
            if (relativeElement == element)
            {
                menuItem.setActive(true);
                menuItem.setEnabled(false);
                ReservationViewPart view = navEntry.getView();
                if(view != null)
                {
                    view.createContent(actuallShownReservation);
                    content.add(view.provideContent());
                }
                else
                {
                    showWarning("Warning", "No content defined");
                }
                NodeList<Node> childNodes = content.getElement().getChildNodes();
                InputUtils.focusOnFirstInput(childNodes);
            }
            else
            {
                menuItem.setActive(false);
                menuItem.setEnabled(true);
            }
        }
    }

    @Override
    public void setPresenter(org.rapla.client.edit.reservation.sample.ReservationView.Presenter presenter)
    {
        super.setPresenter(presenter);
        for (Dual navEntry : navEntries)
        {
            ReservationViewPart view = navEntry.getView();
            if(view != null)
            {
                view.setPresenter(presenter);
            }
        }
    }

    @Override
    public void showWarning(String title, String warning)
    {
        RaplaPopups.showWarning(title, warning);
    }

    @Override
    public void show(final Reservation reservation)
    {
        if(actuallShownReservation != null)
        {
            getPresenter().onCancelButtonClicked(actuallShownReservation);
        }
        actuallShownReservation = reservation;
        // create new one
        popup = RaplaPopups.createNewPopupPanel();
        popup.setAnimationEnabled(true);
        popup.setAnimationType(AnimationType.ROLL_DOWN);
        final Div layout = new Div();
        layout.add(buttons);
        layout.add(bar);
        layout.add(content);
        popup.add(layout);
        popup.center();
        activate(navEntries.get(0).getMenuItem().getElement());
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
        popup.hide();
    }
}
