package org.rapla.client.edit.reservation.sample.gwt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;
import org.rapla.framework.RaplaLocale;
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
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

@DefaultImplementation(of = ReservationView.class,context = InjectionContext.gwt)
public class ReservationViewImpl extends AbstractView<Presenter>implements ReservationView<IsWidget>
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

    public static class ContentWrapper
    {
        private final ArrayList<Dual> navEntries = new ArrayList<Dual>();
        private final Div content = new Div();
        private final Div buttons = new Div();
        private final NavTabs bar = new NavTabs();
        private PopupPanel popup;
        private ReservationViewPart selectedView;
        private final Reservation reservation;

        public ContentWrapper(final Presenter presenter, final RaplaResources i18n, final BundleManager bundleManager, final RaplaLocale raplaLocale,
                Reservation reservation)
        {
            this.reservation = reservation;
            navEntries.add(new Dual(new AnchorListItem("Veranstaltungsinformationen"), new InfoView(i18n, bundleManager, presenter)));
            navEntries.add(new Dual(new AnchorListItem("Termine und Ressourcen"), new ResourceDatesView(i18n, bundleManager, raplaLocale, presenter)));
            navEntries.add(new Dual(new AnchorListItem("Rechte"), null));
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
                        presenter.onSaveButtonClicked(reservation);
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
                        presenter.onCancelButtonClicked(reservation);
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
                        presenter.onDeleteButtonClicked(reservation);
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

        public void updateAppointments(Appointment[] allAppointments, Appointment newSelectedAppointment)
        {
            if (selectedView != null)
            {
                selectedView.updateAppointments(allAppointments, newSelectedAppointment);
            }
        }

        public void hide()
        {
            popup.hide();
            popup.removeFromParent();
        }

    }

    public interface ReservationViewPart
    {

        IsWidget provideContent();

        void createContent(Reservation reservation);

        void updateAppointments(Appointment[] allAppointments, Appointment selectedAppointment);
    }

    private final Map<String, ContentWrapper> openedPopups = new HashMap<String, ContentWrapper>();
    private final RaplaLocale raplaLocale;
    private final RaplaResources i18n;
    private final BundleManager bundleManager;
    private final Logger logger;

    @Inject
    public ReservationViewImpl(Logger logger, RaplaResources i18n, BundleManager bundleManager, RaplaLocale raplaLocale)
    {
        super();
        this.i18n = i18n;
        this.logger = logger;
        this.bundleManager = bundleManager;
        this.raplaLocale = raplaLocale;
    }

    @Override
    public void updateAppointments(Reservation reservation, Appointment[] allAppointments, Appointment newSelectedAppointment)
    {
        final ContentWrapper contentWrapper = openedPopups.get(reservation.getId());
        if (contentWrapper != null)
        {
            contentWrapper.updateAppointments(allAppointments, newSelectedAppointment);
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
        {
            // Already showing... so complete update is expected...
            final ContentWrapper openedPopup = openedPopups.remove(reservation.getId());
            if(openedPopup != null)
            {
                openedPopup.hide();
            }
        }
        final ContentWrapper value = new ContentWrapper(getPresenter(), i18n, bundleManager, raplaLocale, reservation);
        openedPopups.put(reservation.getId(), value);
    }

    public void hide(final Reservation reservation)
    {
        final ContentWrapper contentWrapper = openedPopups.remove(reservation.getId());
        if (contentWrapper != null)
        {
            contentWrapper.hide();
        }
    }
}
