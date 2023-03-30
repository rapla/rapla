package org.rapla.client.edit.reservation.sample.gwt.subviews;

import com.google.gwt.user.client.ui.IsWidget;
import org.gwtbootstrap3.client.ui.html.Div;
import org.rapla.RaplaResources;
import org.rapla.client.edit.reservation.sample.ReservationView.Presenter;
import org.rapla.client.edit.reservation.sample.gwt.ReservationViewImpl.ReservationViewPart;
import org.rapla.components.i18n.BundleManager;
import org.rapla.entities.domain.Appointment;
import org.rapla.entities.domain.Reservation;

import javax.inject.Inject;

public class RightsView implements ReservationViewPart
{

    private final Div content = new Div();
    private final RaplaResources i18n;
    private final BundleManager bundleManager;
    private final Presenter presenter;

    @Inject
    public RightsView(RaplaResources i18n, BundleManager bundleManager, Presenter presenter)
    {
        this.i18n = i18n;
        this.bundleManager = bundleManager;
        this.presenter = presenter;
    }

    @Override
    public IsWidget provideContent()
    {
        return content;
    }

    @Override
    public void createContent(Reservation reservation)
    {
        content.clear();
        // TODO
    }

    @Override
    public void updateAppointments(Appointment[] allAppointments, Appointment selectedAppointment)
    {
    }

}
