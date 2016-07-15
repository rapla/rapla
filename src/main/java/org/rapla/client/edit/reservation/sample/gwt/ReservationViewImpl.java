package org.rapla.client.edit.reservation.sample.gwt;

import java.util.ArrayList;

import javax.inject.Inject;

import org.gwtbootstrap3.client.ui.AnchorListItem;
import org.gwtbootstrap3.client.ui.Button;
import org.gwtbootstrap3.client.ui.NavTabs;
import org.gwtbootstrap3.client.ui.constants.IconSize;
import org.gwtbootstrap3.client.ui.constants.IconType;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.RaplaResources;
import org.rapla.client.edit.reservation.sample.ReservationView;
import org.rapla.client.edit.reservation.sample.gwt.subviews.InfoView;
import org.rapla.client.edit.reservation.sample.gwt.subviews.ResourceDatesView;
import org.rapla.client.edit.reservation.sample.gwt.subviews.RightsView;
import org.rapla.client.gwt.components.InputUtils;
import org.rapla.client.gwt.view.RaplaPopups;
import org.rapla.client.internal.ResourceSelectionView.Presenter;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

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

@DefaultImplementation(of = ReservationView.class, context = InjectionContext.client)
public class ReservationViewImpl implements ReservationView
{

    public static class Dual
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

        IsWidget provideContent();

        void createContent(Reservation reservation);

        void updateAppointments(Appointment[] allAppointments, Appointment selectedAppointment);

    }

    private final ArrayList<Dual> navEntries = new ArrayList<Dual>();
    private final Div content = new Div();
    private final Div buttons = new Div();
    private final NavTabs bar = new NavTabs();
    private final RaplaResources i18n;
    private final BundleManager bundleManager;
    private final RaplaLocale raplaLocale;
    private PopupPanel popup;
    private ReservationViewPart selectedView;
    private Reservation reservation;
    private Presenter presenter;

    @Inject
    public ReservationViewImpl(RaplaResources i18n, BundleManager bundleManager, RaplaLocale raplaLocale)
    {
        this.i18n = i18n;
        this.bundleManager = bundleManager;
        this.raplaLocale = raplaLocale;
    }
    
    @Override
    public void setPresenter(Presenter presenter) {
        this.presenter = presenter;
    }
    
    protected Presenter getPresenter() {
        return presenter;
    }
    
    @Override
    public boolean isVisible()
    {
        return popup != null && popup.isAttached() && popup.isVisible();
    }

    private void createIcon(IconType type, ClickHandler handler, String text)
    {
        Button button = new Button(text);
        button.setIcon(type);
        button.setIconSize(IconSize.TIMES2);
        button.addClickHandler(handler);
        buttons.add(button);
    }

    private void activate(Element relativeElement)
    {
        content.clear();
        for (Dual navEntry : navEntries)
        {
            AnchorListItem menuItem = navEntry.getMenuItem();
            Element element = menuItem.getElement();
            if (relativeElement == element)
            {
                menuItem.setActive(true);
                menuItem.setEnabled(false);
                selectedView = navEntry.getView();
                if (selectedView != null)
                {
                    selectedView.createContent(reservation);
                    content.add(selectedView.provideContent());
                }
                else
                {
                    RaplaPopups.showWarning("Warning", "No content defined");
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

    public void updateAppointments(Appointment newSelectedAppointment)
    {
        if (selectedView != null)
        {
            Appointment[] allAppointments = reservation.getAppointments();
            selectedView.updateAppointments(allAppointments, newSelectedAppointment);
        }
    }

    public void hide()
    {
        popup.hide();
        popup.removeFromParent();
    }

    @Override
    public void showWarning(String title, String warning)
    {
        RaplaPopups.showWarning(title, warning);
    }

    @Override
    public void show(final Reservation reservation)
    {
        this.reservation = reservation;
        navEntries.add(new Dual(new AnchorListItem("Veranstaltungsinformationen"), new InfoView(i18n, bundleManager, getPresenter())));
        navEntries.add(new Dual(new AnchorListItem("Termine und Ressourcen"), new ResourceDatesView(i18n, bundleManager, raplaLocale, getPresenter())));
        navEntries.add(new Dual(new AnchorListItem(i18n.getString("permissions")), new RightsView(i18n, bundleManager, getPresenter())));
        content.setStyleName("content");
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
                    getPresenter().onSaveButtonClicked();
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
                    getPresenter().onCancelButtonClicked();
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
                    getPresenter().onDeleteButtonClicked();
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
                activate(relativeElement);
            }

        }, ClickEvent.getType());
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
}
