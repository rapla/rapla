package org.rapla.client.gwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.ImageIcon;

import org.rapla.components.xmlbundle.I18nBundle;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.facade.RaplaComponent;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.framework.logger.Logger;
import org.rapla.gui.EventCheck;
import org.rapla.gui.PopupContext;
import org.rapla.gui.internal.common.RaplaClipboard;
import org.rapla.gui.internal.edit.reservation.ReservationControllerImpl;
import org.rapla.gui.internal.edit.reservation.ReservationEditFactory;

public class ReservationControllerGWTImpl extends ReservationControllerImpl
{

    @Inject
    public ReservationControllerGWTImpl(ClientFacade facade, RaplaLocale raplaLocale, Logger logger, @Named(RaplaComponent.RaplaResourcesId) I18nBundle i18n, AppointmentFormater appointmentFormater,
            ReservationEditFactory editProvider, CalendarSelectionModel calendarModel, RaplaClipboard clipboard)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, editProvider, calendarModel, clipboard);
    }


    @Override
    protected PopupContext getPopupContext()
    {
        return new GWTPopupContext(null);
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
    protected Collection<EventCheck> getEventChecks() throws RaplaException
    {
        return Collections.emptyList();
    }

}
