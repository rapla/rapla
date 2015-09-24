package org.rapla.client.gwt;

import org.rapla.RaplaResources;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.gui.PopupContext;
import org.rapla.gui.ReservationController;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.gui.internal.edit.reservation.ReservationControllerImpl;
import org.rapla.gui.internal.edit.reservation.ReservationEditFactory;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@DefaultImplementation(of= ReservationController.class, context = InjectionContext.gwt)
public class ReservationControllerGwtImpl extends ReservationControllerImpl
{

    @Inject
    public ReservationControllerGwtImpl(ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n,
            AppointmentFormater appointmentFormater, ReservationEditFactory editProvider, CalendarSelectionModel calendarModel, RaplaClipboard clipboard)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, editProvider, calendarModel, clipboard);
    }


    @Override
    protected PopupContext getPopupContext()
    {
        return new GwtPopupContext(null);
    }

    @Override
    protected void showException(Exception ex, PopupContext sourceComponent)
    {
        getLogger().error(ex.getMessage(), ex);
    }

    @Override
    protected int showDialog(String action, PopupContext context, List<String> optionList, List<String> iconList, String title, String content,
            String dialogIcon) throws RaplaException
    {
        return 0;
    }
    
    @Override
    protected boolean showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException
    {
        return false;
    }

    @Override
    protected Set<Provider<EventCheck>> getEventChecks() throws RaplaException
    {
        return Collections.emptySet();
    }

}
