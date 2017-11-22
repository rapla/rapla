package org.rapla.client.gwt;

import org.rapla.RaplaResources;
import org.rapla.client.PopupContext;
import org.rapla.client.ReservationController;
import org.rapla.client.dialog.DialogInterface;
import org.rapla.client.dialog.DialogUiFactoryInterface;
import org.rapla.client.extensionpoints.EventCheck;
import org.rapla.client.internal.RaplaClipboard;
import org.rapla.client.internal.ReservationControllerImpl;
import org.rapla.entities.domain.AppointmentFormater;
import org.rapla.facade.CalendarSelectionModel;
import org.rapla.facade.ClientFacade;
import org.rapla.framework.RaplaException;
import org.rapla.framework.RaplaLocale;
import org.rapla.inject.DefaultImplementation;
import org.rapla.inject.InjectionContext;
import org.rapla.logger.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@DefaultImplementation(of= ReservationController.class, context = InjectionContext.gwt)
public class ReservationControllerGwtImpl extends ReservationControllerImpl
{
    DialogUiFactoryInterface dialogUiFactory;

    @Inject
    public ReservationControllerGwtImpl(ClientFacade facade, RaplaLocale raplaLocale, Logger logger, RaplaResources i18n,
            AppointmentFormater appointmentFormater, CalendarSelectionModel calendarModel, RaplaClipboard clipboard, DialogUiFactoryInterface dialogUiFactoryInterface)
    {
        super(facade, raplaLocale, logger, i18n, appointmentFormater, calendarModel, clipboard);
        this.dialogUiFactory = dialogUiFactoryInterface;
    }


    @Override
    protected PopupContext getPopupContext()
    {
        return new GwtPopupContext(null);
    }

    @Override
    protected void showException(Throwable ex, PopupContext sourceComponent)
    {
        dialogUiFactory.showException(ex, sourceComponent);
        getLogger().error(ex.getMessage(), ex);
    }

    @Override
    protected int showDialog(String action, PopupContext popupContext, List<String> optionList, List<String> iconList, String title, String content,
            String dialogIcon) throws RaplaException
    {
        DialogInterface dialog = dialogUiFactory.create(
                popupContext
                ,true
                ,title
                ,content
                ,optionList.toArray(new String[] {})
        );
        if ( dialogIcon != null)
        {
            dialog.setIcon(dialogIcon);
        }
        for ( int i=0;i< optionList.size();i++)
        {
            final String string = iconList.get( i);
            if ( string != null)
            {
                dialog.getAction(i).setIcon(string);
            }
        }
        //dialog.setPosition(point.getX(), point.getY());
        dialog.start(true);
        int index = dialog.getSelectedIndex();
        return index;
    }
    
    @Override
    protected boolean showDeleteDialog(PopupContext context, Object[] deletables) throws RaplaException
    {
        return false;
    }

    @Override
    protected Provider<Set<EventCheck>> getEventChecks()
    {
        return new Provider<Set<EventCheck>>()
        {
            @Override
            public Set<EventCheck> get()
            {
                return Collections.emptySet();
            }
        };
    }

}
